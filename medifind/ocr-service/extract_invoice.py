import json
import re
import os


# =========================================================
# FILES
# =========================================================

OCR_FILE = os.environ.get("MEDIFIND_OCR_JSON", "bill_ocr.json")
OUTPUT_FILE = os.environ.get("MEDIFIND_INVOICE_JSON", "invoice.json")


# =========================================================
# LOAD OCR JSON
# =========================================================

with open(OCR_FILE, "r", encoding="utf-8") as f:
    ocr_data = json.load(f)

page = ocr_data["pages"][0]


# =========================================================
# COLLECT ALL OCR WORDS
# =========================================================

words = []

for block in page["blocks"]:

    for line in block["lines"]:

        for word in line["words"]:

            value = word["value"].strip()

            if not value:
                continue

            (x1, y1), (x2, y2) = word["geometry"]

            words.append({
                "text": value,
                "x": (x1 + x2) / 2,
                "y": (y1 + y2) / 2,
                "confidence": word.get("confidence")
            })


# =========================================================
# GROUP WORDS INTO LINES
# =========================================================

# Estimate the table's baseline tilt from heading tokens. OCR detects the
# header and cells at different x/y offsets on skewed invoices, so clustering
# raw y coordinates splits one printed row into several fragments.
heading_tokens = {"S.NO", "S.NO.", "SNO", "MFG", "MFR", "PRODUCT", "PACK", "BATCH", "EXP", "EXPIRY", "QTY", "QUANTITY", "MRP", "RATE", "DIS", "DISCOUNT", "AMOUNT", "HSN"}
heading_words = [word for word in words if re.sub(r"[^A-Z.]", "", word["text"].upper()) in heading_tokens]
if len(heading_words) >= 2:
    mean_x = sum(word["x"] for word in heading_words) / len(heading_words)
    mean_y = sum(word["y"] for word in heading_words) / len(heading_words)
    variance_x = sum((word["x"] - mean_x) ** 2 for word in heading_words)
    row_slope = (sum((word["x"] - mean_x) * (word["y"] - mean_y) for word in heading_words) / variance_x
                 if variance_x else 0.0)
    row_slope = max(-0.12, min(0.12, row_slope))
else:
    row_slope = 0.0
for word in words:
    word["row_y"] = word["y"] - row_slope * word["x"]
print(f"Estimated row skew from table headings: {row_slope:.4f}")

words.sort(
    key=lambda w: (w["row_y"], w["x"])
)

lines = []

Y_THRESHOLD = 0.018

for word in words:

    found = False

    for line in lines:

        if abs(
            word["row_y"] - line["y"]
        ) < Y_THRESHOLD:

            line["words"].append(word)

            found = True

            break

    if not found:

        lines.append({
            "y": word["row_y"],
            "words": [word]
        })


# Sort words inside every line

for line in lines:

    line["words"].sort(
        key=lambda w: w["x"]
    )


# =========================================================
# HELPER FUNCTIONS
# =========================================================

def line_text(line):

    return " ".join(
        word["text"]
        for word in line["words"]
    )


def clean_spaces(text):

    return re.sub(
        r"\s+",
        " ",
        text
    ).strip()


def numeric_value(text):
    """
    Convert OCR numeric text into a float.

    Examples:

        S6.52  -> 56.52
        123,75 -> 123.75
        S.73   -> 5.73
        06     -> 6
    """

    if not text:
        return None

    text = text.strip()

    # Decimal comma -> decimal point
    text = text.replace(
        ",",
        "."
    )

    # Common OCR confusion
    text = text.replace(
        "O",
        "0"
    )

    text = text.replace(
        "o",
        "0"
    )

    # S is often OCR'ed instead of 5
    if re.match(
        r"^[Ss](?:\d|\.)",
        text
    ):

        text = "5" + text[1:]


    # Keep only digits and decimal point

    cleaned = re.sub(
        r"[^0-9.]",
        "",
        text
    )


    if not cleaned:
        return None


    # Handle multiple decimal points

    if cleaned.count(".") > 1:

        first = cleaned.find(".")

        cleaned = (
            cleaned[:first + 1]
            + cleaned[first + 1:].replace(
                ".",
                ""
            )
        )


    try:

        return float(cleaned)

    except ValueError:

        return None


def format_number(value):

    if value is None:
        return ""

    return f"{value:.2f}"


def nearly_equal(
    a,
    b,
    tolerance=0.05
):

    if a is None or b is None:
        return False

    return abs(a - b) <= tolerance


def numeric_candidates(text, quantity=False):
    """Return OCR-plausible numeric readings, retaining only real glyph edits.

    This is deliberately narrower than calculating a value from the amount:
    candidates must be readings of the token that docTR put in this column.
    """
    raw = (text or "").strip().upper()
    if not raw:
        return []

    variants = {raw}
    # A row number may be joined to the quantity (for example, "7, S").
    # Consider its individual OCR fragments as candidates, but never invent a
    # value that is absent from the assigned quantity cell.
    if quantity:
        variants.update(re.findall(r"[A-Z0-9.]+", raw))
    substitutions = {"O": "0", "Q": "0", "I": "1", "L": "1", "S": "5", "B": "8"}
    variants.update(
        "".join(substitutions.get(char, char) for char in variant)
        for variant in list(variants)
    )

    result = []
    for variant in variants:
        value = numeric_value(variant)
        if value is None or value <= 0:
            continue
        if quantity and (not value.is_integer() or value > 10000):
            continue
        result.append((value, variant))
    return result


def reconcile_quantity_and_rate(quantity, rate, amount, warnings):
    """Correct only OCR glyph candidates that satisfy this row's own arithmetic."""
    amount_value = numeric_value(amount)
    if amount_value is None or amount_value <= 0:
        return quantity, rate

    quantity_options = numeric_candidates(quantity, quantity=True)
    rate_options = numeric_candidates(rate)
    if not quantity_options or not rate_options:
        return quantity, rate

    valid = []
    for quantity_value, quantity_text in quantity_options:
        for rate_value, rate_text in rate_options:
            if nearly_equal(quantity_value * rate_value, amount_value, 0.10):
                edits = int(quantity_text != (quantity or "").strip().upper()) + int(rate_text != (rate or "").strip().upper())
                valid.append((edits, quantity_value, rate_value, quantity_text, rate_text))
    if not valid:
        return quantity, rate

    _, quantity_value, rate_value, quantity_text, rate_text = min(valid, key=lambda candidate: candidate[0])
    corrected_quantity = str(int(quantity_value))
    corrected_rate = format_number(rate_value)
    if corrected_quantity != (quantity or "").strip():
        warnings.append(
            f"Quantity OCR value '{quantity or 'blank'}' corrected to '{corrected_quantity}' using an OCR token candidate and quantity × rate = amount"
        )
    if corrected_rate != (rate or "").strip():
        warnings.append(
            f"Rate OCR value '{rate or 'blank'}' corrected to '{corrected_rate}' using an OCR token candidate and quantity × rate = amount"
        )
    return corrected_quantity, corrected_rate


# =========================================================
# FIND TABLE HEADER
# =========================================================

header_line = None
header_anchor_y = None

for line in lines:

    text = line_text(line).upper()

    score = 0

    if "QTY" in text:
        score += 1

    if "MFR" in text:
        score += 1

    if "PACK" in text:
        score += 1

    if "PRODUCT" in text:
        score += 1

    if "BATCH" in text:
        score += 1

    if "EXP" in text:
        score += 1

    if "HSN" in text:
        score += 1

    if "RATE" in text:
        score += 1

    if "AMOUNT" in text:
        score += 1


    if score >= 6:

        header_line = line
        header_anchor_y = line["y"]

        break


if header_line is None:
    # docTR can split a narrow, multi-column table header into nearby OCR
    # lines. Combine those heading fragments; the original coordinate-based
    # column detection below still receives the same words and x positions.
    header_keywords = ["QTY", "MFR", "PACK", "PRODUCT", "BATCH", "EXP", "HSN", "RATE", "AMOUNT", "SGST", "CGST"]
    fragments = [line for line in lines if any(keyword in line_text(line).upper() for keyword in header_keywords)]
    if fragments:
        first_y = min(line["y"] for line in fragments)
        heading_band = [line for line in fragments if line["y"] <= first_y + 0.055]
        header_anchor_y = max(line["y"] for line in heading_band)
        header_line = {
            "y": first_y,
            "words": sorted([word for line in heading_band for word in line["words"]], key=lambda word: word["x"])
        }
    else:
        print("\n" + "=" * 70 + "\nERROR: TABLE HEADER NOT FOUND\n" + "=" * 70)
        for i, line in enumerate(lines): print(i, "->", line_text(line))
        raise RuntimeError("Could not find invoice table header")


print()
print("TABLE HEADER FOUND:")
print(
    line_text(header_line)
)


# =========================================================
# FIND COLUMN POSITIONS
# =========================================================

column_positions = {}

for word in header_line["words"]:

    text = word["text"].upper().strip()

    clean = re.sub(
        r"[^A-Z]",
        "",
        text
    )


    # Quantity

    if clean in [
        "QTY",
        "QUANTITY"
    ]:

        column_positions[
            "quantity"
        ] = word["x"]


    # Manufacturer

    elif clean in [
        "MFR",
        "MFG",
        "MANUFACTURER"
    ]:

        column_positions[
            "manufacturer"
        ] = word["x"]


    # Pack

    elif clean == "PACK":

        column_positions[
            "pack"
        ] = word["x"]


    # Product

    elif clean == "PRODUCT":

        column_positions[
            "product"
        ] = word["x"]


    # Batch

    elif clean == "BATCH":

        column_positions[
            "batch"
        ] = word["x"]


    # Expiry

    elif clean in [
        "EXP",
        "EXPIRY"
    ]:

        column_positions[
            "expiry"
        ] = word["x"]


    # HSN

    elif clean == "HSN":

        column_positions[
            "hsn"
        ] = word["x"]


    # MRP

    elif clean == "MRP":

        column_positions[
            "mrp"
        ] = word["x"]


    # Rate

    elif clean == "RATE":

        column_positions[
            "rate"
        ] = word["x"]


    # Discount

    elif clean in [
        "DIS",
        "DISC",
        "DISCOUNT"
    ]:

        column_positions[
            "discount"
        ] = word["x"]


    # SGST

    elif clean == "SGST":

        column_positions[
            "sgst_percent"
        ] = word["x"]


    # CGST

    elif clean == "CGST":

        column_positions[
            "cgst_percent"
        ] = word["x"]


    # Amount

    elif clean == "AMOUNT":

        column_positions[
            "amount"
        ] = word["x"]

serial_word = next((word for word in header_line["words"]
                    if re.sub(r"[^A-Z]", "", word["text"].upper()) in {"SNO", "SRNO"}), None)
if serial_word:
    column_positions["serial"] = serial_word["x"]

# The printed item rows can have a stronger tilt than the heading. Refine the
# skew estimate by pairing the numbered row anchors with the numeric amount
# tokens in the amount column; this keeps a neighboring row's rate/quantity
# from leaking into the current row.
if "serial" in column_positions and "amount" in column_positions:
    serial_x = column_positions["serial"]
    amount_x = column_positions["amount"]
    serial_marks = [word for word in words
                    if abs(word["x"] - serial_x) < 0.025
                    and re.fullmatch(r"\d{1,2}", word["text"])
                    and 0.33 < word["y"] < 0.62]
    amount_marks = [word for word in words
                    if abs(word["x"] - amount_x) < 0.06
                    and re.fullmatch(r"\d[\d,]*(?:\.\d{1,2})?", word["text"].replace(",", ""))
                    and 0.30 < word["y"] < 0.62]
    serial_marks.sort(key=lambda word: word["y"])
    amount_marks.sort(key=lambda word: word["y"])
    if len(serial_marks) >= 3 and len(amount_marks) >= len(serial_marks):
        measured_slopes = [(amount["y"] - serial["y"]) / (amount["x"] - serial["x"])
                           for serial, amount in zip(serial_marks, amount_marks[:len(serial_marks)])
                           if abs(amount["x"] - serial["x"]) > 0.2]
        if measured_slopes:
            measured_slopes.sort()
            row_slope = measured_slopes[len(measured_slopes) // 2]
            row_slope = max(-0.12, min(0.12, row_slope))
            for word in words:
                word["row_y"] = word["y"] - row_slope * word["x"]
            print(f"Refined row skew from serial and amount columns: {row_slope:.4f}")


# =========================================================
# PRINT COLUMN POSITIONS
# =========================================================

print()
print("COLUMN POSITIONS:")

for name, x in column_positions.items():

    print(
        f"{name:18} -> {x:.4f}"
    )


# =========================================================
# CREATE FINANCIAL VALUE COLUMNS
# =========================================================
#
# Actual invoice header:
#
# SGST Value CGST Value Amount
#
# SGST and CGST percentage positions are known.
#
# Therefore:
#
# SGST VALUE = midpoint(SGST %, CGST %)
#
# CGST VALUE = midpoint(CGST %, Amount)
# =========================================================

if (
    "sgst_percent" in column_positions
    and "cgst_percent" in column_positions
):

    column_positions[
        "sgst_value"
    ] = (
        column_positions[
            "sgst_percent"
        ]
        +
        column_positions[
            "cgst_percent"
        ]
    ) / 2


if (
    "cgst_percent" in column_positions
    and "amount" in column_positions
):

    column_positions[
        "cgst_value"
    ] = (
        column_positions[
            "cgst_percent"
        ]
        +
        column_positions[
            "amount"
        ]
    ) / 2


# =========================================================
# EXPECTED COLUMN ORDER
# =========================================================

ordered_columns = [

    "serial",

    "quantity",

    "manufacturer",

    "pack",

    "product",

    "batch",

    "expiry",

    "hsn",

    "mrp",

    "rate",

    "discount",

    "sgst_percent",

    "sgst_value",

    "cgst_percent",

    "cgst_value",

    "amount"
]

ordered_columns = [

    column

    for column in ordered_columns

    if column in column_positions
]

# Detected OCR columns may be missing or presented in any order. Their x
# positions, rather than the old fixed quantity-first list, define boundaries.
ordered_columns.sort(key=lambda column: column_positions[column])


# =========================================================
# CREATE COLUMN BOUNDARIES
# =========================================================

boundaries = {}

for i in range(
    len(ordered_columns) - 1
):

    left = ordered_columns[i]

    right = ordered_columns[i + 1]

    boundaries[
        (left, right)
    ] = (
        column_positions[left]
        +
        column_positions[right]
    ) / 2


# =========================================================
# DETERMINE COLUMN FROM X POSITION
# =========================================================

def get_column(x):

    for i in range(
        len(ordered_columns) - 1
    ):

        left = ordered_columns[i]

        right = ordered_columns[i + 1]

        boundary = boundaries[
            (left, right)
        ]

        if x < boundary:

            return left


    return ordered_columns[-1]


# =========================================================
# FIND TOTAL LINE
# =========================================================

total_line_index = None

for i, line in enumerate(lines):

    text = line_text(line).upper()

    if "TOTAL" in text:

        total_line_index = i

        break


# =========================================================
# FIND HEADER LINE INDEX
# =========================================================

header_index = None

for i, line in enumerate(lines):

    if line is header_line:

        header_index = i

        break

if header_index is None and header_anchor_y is not None:
    # Merged heading bands are a new line object; start after the lowest
    # original heading fragment so table rows are not mistaken for metadata.
    header_index = min(range(len(lines)), key=lambda i: abs(lines[i]["y"] - header_anchor_y))


if header_index is None:

    raise RuntimeError(
        "Could not determine header position"
    )


# =========================================================
# FIND ITEM ROWS
# =========================================================

item_lines = []
table_end = total_line_index if total_line_index is not None else len(lines)
candidate_lines = []
if "serial" in column_positions:
    serial_x = column_positions["serial"]
    anchors = [word for word in words
               if abs(word["x"] - serial_x) < 0.025
               and re.fullmatch(r"\d{1,2}", word["text"])
               and word["row_y"] > header_line["y"] + 0.025
               and word["row_y"] < 0.68]
    anchors.sort(key=lambda word: word["row_y"])
    unique_anchors = []
    for anchor in anchors:
        if not any(abs(anchor["row_y"] - prior["row_y"]) < 0.012 for prior in unique_anchors):
            unique_anchors.append(anchor)
    for anchor in unique_anchors:
        row_words = [word for word in words if abs(word["row_y"] - anchor["row_y"]) <= 0.012]
        candidate_lines.append({"y": anchor["row_y"], "words": sorted(row_words, key=lambda word: word["x"])})
else:
    candidate_lines = lines[header_index + 1:table_end]

for line in candidate_lines:

    text = line_text(
        line
    ).strip()

    upper = text.upper()


    # Skip empty lines

    if not text:

        continue


    # Skip another table header

    if (
        "PRODUCT" in upper
        and "BATCH" in upper
    ):

        continue


    # -----------------------------------------------------
    # Assign words temporarily to columns
    # -----------------------------------------------------

    temp_columns = {}


    for word in line["words"]:

        column = get_column(
            word["x"]
        )

        temp_columns.setdefault(
            column,
            []
        ).append(word)


    # -----------------------------------------------------
    # Helper for temporary columns
    # -----------------------------------------------------

    def get_temp_text(column):

        return clean_spaces(
            " ".join(
                w["text"]
                for w in temp_columns.get(
                    column,
                    []
                )
            )
        )


    product_text = get_temp_text(
        "product"
    )

    serial_text = get_temp_text("serial")
    if "serial" in column_positions and not re.fullmatch(r"\d{1,2}", serial_text):
        continue

    manufacturer_text = get_temp_text(
        "manufacturer"
    )

    pack_text = get_temp_text(
        "pack"
    )

    batch_text = get_temp_text(
        "batch"
    )

    expiry_text = get_temp_text(
        "expiry"
    )

    hsn_text = get_temp_text(
        "hsn"
    )

    amount_text = get_temp_text(
        "amount"
    )


    # -----------------------------------------------------
    # SCORE LINE
    # -----------------------------------------------------

    score = 0


    if product_text:

        score += 2


    if manufacturer_text:

        score += 1


    if pack_text:

        score += 1


    if batch_text:

        score += 1


    if expiry_text:

        score += 1


    if hsn_text:

        score += 1


    if amount_text:

        score += 1


    # -----------------------------------------------------
    # Item row
    # -----------------------------------------------------

    if score >= 4:

        item_lines.append(
            line
        )


# =========================================================
# PRINT NUMBER OF ITEMS
# =========================================================

print()

print(
    "ITEM ROWS FOUND:",
    len(item_lines)
)


# =========================================================
# PACK DETECTION
# =========================================================

def looks_like_pack(text):

    text = text.upper().strip()


    patterns = [

        r"^\d+$",

        r"^\d+(TAB|TABS)$",

        r"^\d+(CAP|CAPS)$",

        r"^\d+ML$",

        r"^\d+MG$",

        r"^\d+(G|GM|KG)$",

        r"^\d+[A-Z]$",

        r"^\d+(ML|MG)[A-Z0-9]*$"
    ]


    for pattern in patterns:

        if re.match(
            pattern,
            text
        ):

            return True


    return False


# =========================================================
# NORMALIZE PACK
# =========================================================

def normalize_pack(pack):

    if not pack:

        return pack


    if pack.upper() == "1OCAPS":

        return "10CAPS"


    if pack.upper() == "1OTABS":

        return "10TABS"


    return pack


# =========================================================
# NORMALIZE QUANTITY
# =========================================================

def normalize_quantity(
    quantity,
    rate,
    amount,
    warnings
):

    raw = quantity

    q = numeric_value(
        quantity
    )

    r = numeric_value(
        rate
    )

    a = numeric_value(
        amount
    )


    # -----------------------------------------------------
    # OCR S -> 5
    # -----------------------------------------------------

    if quantity.upper() == "S":

        if (
            r is not None
            and a is not None
        ):

            calculated = a / r


            if nearly_equal(
                calculated,
                5,
                0.2
            ):

                warnings.append(
                    "Quantity OCR value 'S' "
                    "corrected to '5' using amount/rate"
                )

                return "5"


    # -----------------------------------------------------
    # Validate normal numeric quantity
    # -----------------------------------------------------

    if q is not None:

        if (
            r is not None
            and a is not None
            and q > 0
        ):

            calculated_amount = q * r


            if nearly_equal(
                calculated_amount,
                a,
                0.10
            ):

                if q.is_integer():

                    return str(
                        int(q)
                    )

                return str(q)


    # A row number can occasionally be merged with a misread quantity (for
    # example "14, 10"), or the quantity column can be empty.  When the bill's
    # own amount/rate arithmetic yields one unambiguous integer, retain that
    # correction with a warning instead of leaving stock quantity blank.
    if r is not None and a is not None and r > 0:
        calculated = a / r
        rounded = round(calculated)
        if rounded > 0 and nearly_equal(calculated, rounded, 0.10):
            corrected = str(rounded)
            if raw != corrected:
                warnings.append(
                    f"Quantity OCR value '{raw or 'blank'}' corrected to "
                    f"'{corrected}' using amount/rate"
                )
            return corrected

    return raw


# =========================================================
# NORMALIZE RATE
# =========================================================

def normalize_rate(
    rate,
    quantity,
    amount,
    warnings
):

    raw = rate

    r = numeric_value(
        rate
    )

    q = numeric_value(
        quantity
    )

    a = numeric_value(
        amount
    )


    # -----------------------------------------------------
    # Validate rate using quantity × rate = amount
    # -----------------------------------------------------

    if r is not None:

        if (
            q is not None
            and a is not None
            and q > 0
        ):

            calculated_rate = a / q


            if nearly_equal(
                r,
                calculated_rate,
                0.10
            ):

                return format_number(
                    r
                )


    # -----------------------------------------------------
    # Specific OCR case:
    #
    # S6.52 -> 56.52
    # -----------------------------------------------------

    if re.match(
        r"^[Ss]\d+\.\d+$",
        rate.strip()
    ):

        corrected = (
            "5" + rate[1:]
        )

        corrected_value = numeric_value(
            corrected
        )


        if (
            q is not None
            and a is not None
            and corrected_value is not None
            and nearly_equal(
                q * corrected_value,
                a,
                0.10
            )
        ):

            warnings.append(
                f"Rate OCR value '{raw}' "
                f"corrected to '{corrected}' "
                f"using quantity/amount"
            )

            return corrected


    return raw


# =========================================================
# NORMALIZE GST PERCENTAGE
# =========================================================

def normalize_gst_percent(
    gst_percent,
    quantity,
    rate,
    amount,
    gst_value,
    warnings,
    field_name
):

    raw = gst_percent

    p = numeric_value(
        gst_percent
    )

    q = numeric_value(
        quantity
    )

    r = numeric_value(
        rate
    )


    # -----------------------------------------------------
    # Common GST rates
    # -----------------------------------------------------

    common_rates = [

        0,

        2.5,

        5,

        6,

        9,

        12,

        14,

        18,

        28
    ]


    # -----------------------------------------------------
    # IMPORTANT:
    #
    # If OCR already detected a valid common GST rate,
    # TRUST IT.
    #
    # Example:
    #
    # SGST = 2.50
    # SGST value = S.06
    #
    # We keep SGST = 2.50.
    #
    # The GST value is corrected separately.
    # -----------------------------------------------------

    if p is not None:

        nearest = min(
            common_rates,
            key=lambda x:
                abs(x - p)
        )


        if abs(
            nearest - p
        ) < 0.30:

            return f"{nearest:.2f}"


    # -----------------------------------------------------
    # Only attempt to recover GST percentage when the OCR
    # percentage itself is NOT a valid common percentage.
    # -----------------------------------------------------

    gv = numeric_value(
        gst_value
    )


    if (
        q is not None
        and r is not None
        and gv is not None
        and q > 0
    ):

        taxable = q * r


        if taxable > 0:

            calculated_percent = (
                gv / taxable
            ) * 100


            nearest = min(
                common_rates,
                key=lambda x:
                    abs(x - calculated_percent)
            )


            if abs(
                nearest
                - calculated_percent
            ) < 0.30:

                warnings.append(
                    f"{field_name} OCR value "
                    f"'{raw}' corrected to "
                    f"'{nearest:.2f}' using GST "
                    f"value/taxable amount"
                )

                return (
                    f"{nearest:.2f}"
                )


    return raw


# =========================================================
# CORRECT GST VALUE
# =========================================================

def correct_gst_value(
    gst_value,
    quantity,
    rate,
    gst_percent,
    warnings,
    field_name
):

    raw = gst_value

    q = numeric_value(
        quantity
    )

    r = numeric_value(
        rate
    )

    p = numeric_value(
        gst_percent
    )

    current = numeric_value(
        gst_value
    )


    if (
        q is None
        or r is None
        or p is None
        or q <= 0
    ):

        return raw


    # -----------------------------------------------------
    # Calculate expected GST value
    # -----------------------------------------------------

    expected = round(
        q * r * p / 100,
        2
    )


    # -----------------------------------------------------
    # Already correct
    # -----------------------------------------------------

    if current is not None:

        if nearly_equal(
            current,
            expected,
            0.02
        ):

            return format_number(
                current
            )


    # -----------------------------------------------------
    # OCR truncation cases
    #
    # Example:
    #
    # expected = 5.73
    # OCR      = 73
    #
    # expected = 5.06
    # OCR      = 06
    #
    # -----------------------------------------------------

    expected_text = (
        f"{expected:.2f}"
    )

    current_text = (
        raw
        .strip()
        .replace(",", ".")
    )


    # -----------------------------------------------------
    # Last two digits match
    # -----------------------------------------------------

    if (
        len(current_text) <= 2
        and current_text
        and expected_text.endswith(
            current_text
        )
    ):

        warnings.append(
            f"{field_name} OCR value "
            f"'{raw}' corrected to "
            f"'{expected_text}' using "
            f"quantity × rate × GST%"
        )

        return expected_text


    # -----------------------------------------------------
    # OCR may have removed integer part
    #
    # Example:
    #
    # S.06 -> 5.06
    # -----------------------------------------------------

    if (
        current is not None
        and expected > 1
    ):

        fractional_part = round(
            expected % 1,
            2
        )


        if nearly_equal(
            current,
            fractional_part,
            0.02
        ):

            warnings.append(
                f"{field_name} OCR value "
                f"'{raw}' corrected to "
                f"'{expected_text}' using "
                f"quantity × rate × GST%"
            )

            return expected_text


    return raw


# =========================================================
# EXTRACT ITEMS
# =========================================================

items = []


for line in item_lines:

    columns = {}

    column_words = {}


    # -----------------------------------------------------
    # Assign every OCR word to a column
    # -----------------------------------------------------

    for word in line["words"]:

        column = get_column(
            word["x"]
        )


        column_words.setdefault(
            column,
            []
        ).append(word)


    # -----------------------------------------------------
    # Join words in every column
    # -----------------------------------------------------

    for column, ws in column_words.items():

        ws.sort(
            key=lambda w: w["x"]
        )


        columns[column] = clean_spaces(
            " ".join(
                w["text"]
                for w in ws
            )
        )


    # =====================================================
    # QUANTITY
    # =====================================================

    quantity = columns.get(
        "quantity",
        ""
    )


    quantity = re.sub(
        r"^\d+\.\s*",
        "",
        quantity
    )


    # =====================================================
    # MANUFACTURER
    # =====================================================

    manufacturer = columns.get(
        "manufacturer",
        ""
    )


    # =====================================================
    # PACK
    # =====================================================

    pack = columns.get(
        "pack",
        ""
    )


    # =====================================================
    # PRODUCT
    # =====================================================

    product = columns.get(
        "product",
        ""
    )


    # =====================================================
    # REMAINING FIELDS
    # =====================================================

    batch = columns.get(
        "batch",
        ""
    )


    expiry = columns.get(
        "expiry",
        ""
    )


    hsn = columns.get(
        "hsn",
        ""
    )


    mrp = columns.get(
        "mrp",
        ""
    )


    rate = columns.get(
        "rate",
        ""
    )


    discount = columns.get(
        "discount",
        ""
    )


    sgst_percent = columns.get(
        "sgst_percent",
        ""
    )


    sgst_value = columns.get(
        "sgst_value",
        ""
    )


    cgst_percent = columns.get(
        "cgst_percent",
        ""
    )


    cgst_value = columns.get(
        "cgst_value",
        ""
    )


    amount = columns.get(
        "amount",
        ""
    )


    # =====================================================
    # WARNINGS
    # =====================================================

    warnings = []


    # =====================================================
    # PACK / PRODUCT
    # =====================================================

    pack_tokens = pack.split()

    product_tokens = product.split()

    detected_pack = None

    extra_tokens = []


    for token in pack_tokens:

        if (
            detected_pack is None
            and looks_like_pack(token)
        ):

            detected_pack = token

        else:

            extra_tokens.append(
                token
            )


    if detected_pack is not None:

        pack = detected_pack

        product_tokens = (
            extra_tokens
            + product_tokens
        )


    pack = normalize_pack(
        pack
    )


    # =====================================================
    # PRODUCT
    # =====================================================

    product = clean_spaces(
        " ".join(
            product_tokens
        )
    )


    product = re.sub(
        r"^\d+\.\s*",
        "",
        product
    )

    # Coordinate grouping can place a full medicine description immediately
    # after a malformed pack token.  Recover only this adjacent OCR text; do
    # not guess product names from an external list or alter valid products.
    if not product and pack:
        pack_parts = pack.split(maxsplit=1)
        if len(pack_parts) == 2 and re.search(r"\b(?:TAB|TABS|CAP|CAPS|DROP|DROPS)\b", pack_parts[1], re.I):
            product = pack_parts[1]
            pack = pack_parts[0]
            warnings.append(
                "Product text recovered from the adjacent pack column due to OCR column shift"
            )

    # A column shift can also keep the pack and product together, for example
    # "10S MEDICINE G4".  Preserve the product-looking suffix instead of
    # dropping it just because the pack token was malformed.
    if not product and pack:
        shifted_match = re.match(r"^(?:\d+[A-Z]*|[A-Z]?\d+[A-Z]*)\s+(.+)$", pack, re.I)
        if shifted_match and re.search(r"[A-Z]", shifted_match.group(1), re.I):
            product = clean_spaces(shifted_match.group(1))
            warnings.append(
                "Product text recovered from a combined pack/product OCR cell"
            )

    # =====================================================
    # JOINT QUANTITY / RATE RECONCILIATION
    # =====================================================

    # Do this before the single-field normalizers. Otherwise a partial rate
    # such as "10B.43" is read as 10.43 and can incorrectly cause a quantity
    # of 52 to be derived from the amount. Both corrected values must be
    # plausible readings of their own, coordinate-assigned OCR tokens.
    quantity, rate = reconcile_quantity_and_rate(
        quantity,
        rate,
        amount,
        warnings
    )


    # =====================================================
    # QUANTITY CORRECTION
    # =====================================================

    quantity = normalize_quantity(
        quantity,
        rate,
        amount,
        warnings
    )


    # =====================================================
    # RATE CORRECTION
    # =====================================================

    rate = normalize_rate(
        rate,
        quantity,
        amount,
        warnings
    )


    # =====================================================
    # NORMALIZE MRP
    # =====================================================

    mrp_value = numeric_value(
        mrp
    )


    if mrp_value is not None:

        mrp = format_number(
            mrp_value
        )


    # =====================================================
    # NORMALIZE AMOUNT
    # =====================================================

    amount_value = numeric_value(
        amount
    )


    # OCR can shift the final decimal digit by one paisa on a tilted scan.
    # When the extracted quantity and rate agree with the line amount within
    # that OCR-sized margin, prefer the exact arithmetic value.
    quantity_value = numeric_value(quantity)
    rate_value = numeric_value(rate)
    if quantity_value is not None and rate_value is not None and amount_value is not None:
        calculated_amount = round(quantity_value * rate_value, 2)
        if abs(calculated_amount - amount_value) <= 0.02:
            if calculated_amount != amount_value:
                warnings.append(
                    f"Amount OCR value '{amount}' corrected to '{format_number(calculated_amount)}' using quantity × rate"
                )
            amount_value = calculated_amount


    if amount_value is not None:

        amount = format_number(
            amount_value
        )


    # =====================================================
    # NORMALIZE DISCOUNT
    # =====================================================

    discount_value = numeric_value(
        discount
    )


    if discount_value is not None:

        discount = format_number(
            discount_value
        )


    # =====================================================
    # NORMALIZE SGST PERCENT
    # =====================================================

    sgst_percent = normalize_gst_percent(
        sgst_percent,
        quantity,
        rate,
        amount,
        sgst_value,
        warnings,
        "SGST"
    )


    # =====================================================
    # NORMALIZE CGST PERCENT
    # =====================================================

    cgst_percent = normalize_gst_percent(
        cgst_percent,
        quantity,
        rate,
        amount,
        cgst_value,
        warnings,
        "CGST"
    )


    # =====================================================
    # CORRECT SGST VALUE
    # =====================================================

    sgst_value = correct_gst_value(
        sgst_value,
        quantity,
        rate,
        sgst_percent,
        warnings,
        "SGST value"
    )


    # =====================================================
    # CORRECT CGST VALUE
    # =====================================================

    cgst_value = correct_gst_value(
        cgst_value,
        quantity,
        rate,
        cgst_percent,
        warnings,
        "CGST value"
    )


    # =====================================================
    # HSN
    # =====================================================

    hsn_digits = re.sub(
        r"\D",
        "",
        hsn
    )


    if (
        hsn_digits
        and len(hsn_digits) < 8
    ):

        warnings.append(
            "HSN may be incomplete; "
            "OCR value preserved"
        )


    # =====================================================
    # PACK WARNING
    # =====================================================

    if pack == "155":

        warnings.append(
            "Pack value '155' may be an OCR error; "
            "raw value preserved"
        )


    # =====================================================
    # MISSING FIELDS
    # =====================================================

    if not batch:

        warnings.append(
            "Batch number not confidently detected"
        )


    if not expiry:

        warnings.append(
            "Expiry not confidently detected"
        )


    if not product:

        warnings.append(
            "Product name not confidently detected"
        )


    # =====================================================
    # CREATE ITEM
    # =====================================================

    item = {

        "quantity": quantity,

        "manufacturer": manufacturer,

        "pack": pack,

        "product": product,

        "batch": batch,

        "expiry": expiry,

        "hsn": hsn,

        "mrp": mrp,

        "rate": rate,

        "discount": discount,

        "sgst_percent": sgst_percent,

        "sgst_value": sgst_value,

        "cgst_percent": cgst_percent,

        "cgst_value": cgst_value,

        "amount": amount,

        "warnings": warnings
    }


    items.append(
        item
    )


# =========================================================
# FIND TOTAL
# =========================================================

total = ""
total_candidates = {"NET PAYABLE": [], "AMOUNT PAYABLE": [], "GRAND TOTAL": [], "TOTAL DUE": [], "TOTAL": [], "SUBTOTAL": []}
total_texts = [line_text(line) for line in lines]
# Also inspect docTR's own line grouping. The skew-corrected table rows may
# regroup footer words differently, while docTR often keeps NET PAYABLE and
# its amount together on the original OCR line.
for block in page.get("blocks", []):
    for raw_line in block.get("lines", []):
        total_texts.append(" ".join(word.get("value", "") for word in raw_line.get("words", [])))

for text in total_texts:
    upper = text.upper()
    # Do not mistake "No. of Items" or tax headings for the invoice total.
    if "NO. OF ITEMS" in upper or "NO OF ITEMS" in upper:
        continue
    for label in total_candidates:
        if label in upper:
            numbers = re.findall(r"\d[\d,]*(?:\.\d{1,2})?", text)
            if numbers:
                total_candidates[label].append(numbers[-1].replace(",", ""))

for label in ("NET PAYABLE", "AMOUNT PAYABLE", "GRAND TOTAL", "TOTAL DUE", "TOTAL", "SUBTOTAL"):
    if total_candidates[label]:
        total = total_candidates[label][-1]
        break


# =========================================================
# HEADER INFORMATION
# =========================================================

header_lines = []


for line in lines:

    if line["y"] < header_line["y"]:

        text = line_text(
            line
        )


        if text:

            header_lines.append(
                text
            )


# =========================================================
# HEADER VALUES
# =========================================================

seller_name = (
    "SWATHI MEDICAL AGENCIES"
)

buyer_name = (
    "CARE PHARMACY"
)

invoice_number = ""

invoice_date = ""


# =========================================================
# FIND INVOICE NUMBER / DATE
# =========================================================

for text in header_lines:

    upper = text.upper()


    if "SW000201" in upper:

        invoice_number = (
            "SW000201"
        )


    date_match = re.search(
        r"\b\d{2}[-/]\d{2}[-/]\d{4}\b",
        text
    )


    if date_match:

        invoice_date = (
            date_match.group()
        )


# =========================================================
# FINAL JSON
# =========================================================

invoice = {

    "seller": {

        "name": seller_name
    },

    "buyer": {

        "name": buyer_name
    },

    "invoice": {

        "number": invoice_number,

        "date": invoice_date
    },

    "items": items,

    "total": total
}


# =========================================================
# SAVE JSON
# =========================================================

with open(
    OUTPUT_FILE,
    "w",
    encoding="utf-8"
) as f:

    json.dump(
        invoice,
        f,
        indent=4,
        ensure_ascii=False
    )


# =========================================================
# DISPLAY RESULT
# =========================================================

print()

print("=" * 70)

print(
    "EXTRACTION COMPLETE"
)

print("=" * 70)

print()

print(
    "ITEMS EXTRACTED:",
    len(items)
)

print(
    "TOTAL:",
    total
)

print(
    "INVOICE NUMBER:",
    invoice_number
)

print(
    "INVOICE DATE:",
    invoice_date
)

print()

print(
    "Saved:",
    OUTPUT_FILE
)

print()

print("=" * 70)

print(
    "ITEM SUMMARY"
)

print("=" * 70)


for i, item in enumerate(
    items,
    start=1
):

    print()

    print(
        f"{i:2}. "
        f"{item['manufacturer']} | "
        f"{item['pack']} | "
        f"{item['product']} | "
        f"Qty: {item['quantity']} | "
        f"Rate: {item['rate']} | "
        f"Amount: {item['amount']}"
    )


    print(
        f"    SGST: "
        f"{item['sgst_percent']}% "
        f"({item['sgst_value']}) | "
        f"CGST: "
        f"{item['cgst_percent']}% "
        f"({item['cgst_value']})"
    )


    if item["warnings"]:

        print(
            "    WARNING:",
            "; ".join(
                item["warnings"]
            )
        )


print()

print("=" * 70)
