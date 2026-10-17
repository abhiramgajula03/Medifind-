import json
import re
import os


# =========================================================
# FILES
# =========================================================

OCR_FILE = os.environ.get("MEDIFIND_OCR_JSON", "bill_ocr.json")
OUTPUT_FILE = os.environ.get("MEDIFIND_HEADER_JSON", "header.json")


# =========================================================
# LOAD OCR
# =========================================================

with open(OCR_FILE, "r", encoding="utf-8") as f:
    ocr_data = json.load(f)

page = ocr_data["pages"][0]


# =========================================================
# COLLECT WORDS
# =========================================================

words = []

for block in page["blocks"]:

    for line in block["lines"]:

        for word in line["words"]:

            text = word["value"].strip()

            if not text:
                continue

            (x1, y1), (x2, y2) = word["geometry"]

            words.append({
                "text": text,
                "x": (x1 + x2) / 2,
                "y": (y1 + y2) / 2,
                "x1": x1,
                "x2": x2,
                "y1": y1,
                "y2": y2,
                "confidence": word.get("confidence")
            })


# =========================================================
# GROUP WORDS INTO LINES
# =========================================================

words.sort(
    key=lambda w: (w["y"], w["x"])
)

Y_THRESHOLD = 0.018

lines = []


for word in words:

    found = False

    for line in lines:

        if abs(
            word["y"] - line["y"]
        ) < Y_THRESHOLD:

            line["words"].append(word)

            found = True

            break


    if not found:

        lines.append({
            "y": word["y"],
            "words": [word]
        })


for line in lines:

    line["words"].sort(
        key=lambda w: w["x"]
    )


# =========================================================
# HELPER FUNCTIONS
# =========================================================

def text_of(line):

    return " ".join(
        w["text"]
        for w in line["words"]
    ).strip()


def clean(text):

    return re.sub(
        r"\s+",
        " ",
        text
    ).strip()


def upper(text):

    return clean(text).upper()


def digits(text):

    return re.sub(
        r"\D",
        "",
        text
    )


# =========================================================
# FIND TABLE HEADER
# =========================================================

table_header = None


for line in lines:

    text = upper(
        text_of(line)
    )

    score = 0

    for keyword in [
        "QTY",
        "MFR",
        "PACK",
        "PRODUCT",
        "BATCH",
        "EXP",
        "HSN",
        "RATE",
        "AMOUNT"
    ]:

        if keyword in text:

            score += 1


    if score >= 6:

        table_header = line

        break


if table_header is None:
    # docTR may emit narrow table headings as adjacent lines rather than one
    # complete line. Keep the original high-confidence path above, but use the
    # earliest heading band as the boundary for the existing header parser.
    heading_lines = []
    for line in lines:
        text = upper(text_of(line))
        if any(keyword in text for keyword in ["QTY", "MFR", "PACK", "PRODUCT", "BATCH", "EXP", "HSN", "RATE", "AMOUNT"]):
            heading_lines.append(line)
    if heading_lines:
        table_header = min(heading_lines, key=lambda line: line["y"])
    else:
        raise RuntimeError("Could not find table header")


table_y = table_header["y"]


# =========================================================
# HEADER AREA
# =========================================================

header_lines = [

    line
    for line in lines
    if line["y"] < table_y

]


print()
print("=" * 70)
print("OCR HEADER")
print("=" * 70)


for line in header_lines:

    print(
        f"{line['y']:.4f} -> {text_of(line)}"
    )


# =========================================================
# SELLER / BUYER
# =========================================================

seller_name = ""
buyer_name = ""


# ---------------------------------------------------------
# SELLER
# ---------------------------------------------------------
#
# Seller is on the left side.
#
# We look for the first meaningful line before
# "Pharmaceutical", "D.No", etc.
# ---------------------------------------------------------

for line in header_lines:

    text = text_of(line)

    if not text:

        continue

    # Only left portion

    left_words = [

        w
        for w in line["words"]
        if w["x"] < 0.40

    ]

    if not left_words:

        continue

    left_text = clean(
        " ".join(
            w["text"]
            for w in left_words
        )
    )

    left_upper = upper(
        left_text
    )


    # Skip generic labels

    if left_upper in [
        "GST INVOICE",
        "CREDIT",
        "DEBIT"
    ]:

        continue


    if (
        "PHARMACEUTICAL" in left_upper
        or "DISTRIBUTOR" in left_upper
        or "DISTRIBUTORS" in left_upper
        or "D.NO" in left_upper
        or "PHONE" in left_upper
        or "GSTIN" in left_upper
        or "PIN" in left_upper
        or "WARANGAL" in left_upper
    ):

        continue


    # First useful left-side line is likely seller

    seller_name = left_text

    break


# ---------------------------------------------------------
# BUYER
# ---------------------------------------------------------
#
# IMPORTANT:
# We don't rely on x > 0.65.
#
# We specifically find the line containing
# "PARTY NAME" and take the text AFTER the label.
# ---------------------------------------------------------

for line in header_lines:

    ws = line["words"]

    for i, word in enumerate(ws):

        if "PARTY" in word["text"].upper():

            # Search next few words for NAME

            party_index = i

            collected = []

            # Everything after Party Name:
            for w in ws[party_index + 1:]:

                collected.append(
                    w["text"]
                )


            candidate = clean(
                " ".join(collected)
            )


            candidate = re.sub(
                r"^NAME\s*:?\s*",
                "",
                candidate,
                flags=re.IGNORECASE
            )


            if candidate:

                buyer_name = candidate

                break


    if buyer_name:

        break


# ---------------------------------------------------------
# FALLBACK BUYER
# ---------------------------------------------------------
#
# If OCR split "Party Name" and buyer name differently,
# look at the first meaningful right-side line.
# ---------------------------------------------------------

if not buyer_name:

    for line in header_lines:

        right_words = [

            w
            for w in line["words"]
            if w["x"] > 0.65

        ]

        if not right_words:

            continue

        candidate = clean(
            " ".join(
                w["text"]
                for w in right_words
            )
        )

        candidate_upper = upper(
            candidate
        )


        if "PARTY" in candidate_upper:

            candidate = re.sub(
                r"PARTY\s*NAME\s*:?",
                "",
                candidate,
                flags=re.IGNORECASE
            ).strip()


        if candidate and candidate.upper() != "PARTY NAME":

            buyer_name = candidate

            break


# =========================================================
# PHONE NUMBERS
# =========================================================

phones = []


for line in header_lines:

    text = text_of(line)

    found_phones = re.findall(
        r"\b\d{10}\b",
        text
    )

    phones.extend(
        found_phones
    )


phones = list(
    dict.fromkeys(phones)
)


seller_phone = ""

buyer_phone = ""


if len(phones) >= 1:

    seller_phone = phones[0]


if len(phones) >= 2:

    buyer_phone = phones[1]


# =========================================================
# GSTIN
# =========================================================

# Standard GSTIN:
#
# 22AAAAA0000A1Z5
#
# 15 characters

GST_PATTERN = re.compile(
    r"\b\d{2}[A-Z]{5}\d{4}[A-Z][A-Z0-9]Z[A-Z0-9]\b",
    re.IGNORECASE
)

gstins = []


for line in header_lines:

    text = text_of(line)

    matches = GST_PATTERN.findall(
        text
    )

    for gst in matches:

        gst = gst.upper()

        if gst not in gstins:

            gstins.append(gst)

seller_gstin = ""

buyer_gstin = ""


if len(gstins) >= 1:

    seller_gstin = gstins[0]


if len(gstins) >= 2:

    buyer_gstin = gstins[1]


# =========================================================
# LOOK FOR MALFORMED GSTIN VALUES
# =========================================================
#
# We don't silently correct these.
# We report them as warnings.
# =========================================================

gst_warnings = []


for line in header_lines:

    text = text_of(line)

    upper_text = upper(text)


    if "GSTIN" in upper_text:

        # Everything after GSTIN
        pieces = re.split(
            r"GSTIN\s*:?",
            text,
            flags=re.IGNORECASE
        )


        if len(pieces) > 1:

            candidate = pieces[-1].strip()

            # Remove spaces/punctuation

            candidate_clean = re.sub(
                r"[^A-Za-z0-9]",
                "",
                candidate
            )


            # If it contains something that looks
            # GST-like but isn't 15 characters

            if (
                len(candidate_clean) >= 10
                and len(candidate_clean) != 15
            ):

                gst_warnings.append(
                    "Possible OCR error in GSTIN: "
                    + candidate_clean
                )


# =========================================================
# INVOICE NUMBER
# =========================================================

invoice_number = ""


for line in header_lines:

    text = text_of(line)

    upper_text = upper(text)


    if (
        "INVOICE NO" in upper_text
        or "INVO" in upper_text
    ):

        # Look for alphanumeric invoice ID

        matches = re.findall(
            r"\b[A-Z]{1,6}\d{3,12}\b",
            text,
            re.IGNORECASE
        )


        for candidate in matches:

            if candidate.upper() not in [
                "INVOICE",
                "INVO"
            ]:

                invoice_number = candidate

                break


    if invoice_number:

        break


# =========================================================
# FALLBACK INVOICE NUMBER
# =========================================================

if not invoice_number:

    for line in header_lines:

        text = text_of(line)

        match = re.search(
            r"\b[A-Z]{2}\d{6}\b",
            text,
            re.IGNORECASE
        )


        if match:

            invoice_number = match.group()

            break


# =========================================================
# DATE EXTRACTION
# =========================================================

DATE_PATTERN = re.compile(
    r"\b\d{2}[-/]\d{2}[-/]\d{4}\b"
)


all_dates = []


for line in header_lines:

    text = text_of(line)

    matches = DATE_PATTERN.findall(
        text
    )

    all_dates.extend(
        matches
    )


# Remove duplicates but preserve order

all_dates = list(
    dict.fromkeys(
        all_dates
    )
)


# =========================================================
# INVOICE DATE
# =========================================================

invoice_date = ""

# Prefer an explicitly labelled invoice date. Do not let dates embedded in a
# seller's drug licence number (for example DL/21/01/2009-2901) win the fallback.
for line in header_lines:
    text = text_of(line)
    for match in DATE_PATTERN.finditer(text):
        prefix = text[:match.start()]
        local_prefix = prefix[-32:]
        if re.search(r"\bDL\s+DATE\s*:?\s*$", local_prefix, re.IGNORECASE):
            continue
        if re.search(r"\bDATE\s*:?\s*$", local_prefix, re.IGNORECASE):
            invoice_date = match.group()
            break
    if invoice_date:
        break


for line in header_lines:

    if invoice_date:
        break

    text = text_of(line)

    upper_text = upper(text)


    # OCR may write "Involça", "Invoiça", etc.
    # Therefore we use date position / nearby labels.

    if (
        "INVOICE" in upper_text
        or "INVOL" in upper_text
        or "INVO" in upper_text
    ):

        dates = DATE_PATTERN.findall(
            text
        )

        if dates:

            invoice_date = dates[0]

            break


# Fallback: first date in header

if not invoice_date and all_dates:
    for line in header_lines:
        text = text_of(line)
        if re.search(r"\bDL\s*(?:NO|DATE|NOS)?\b", text, re.IGNORECASE):
            continue
        dates = DATE_PATTERN.findall(text)
        if dates:
            invoice_date = dates[0]
            break


# =========================================================
# DUE DATE
# =========================================================

due_date = ""


for line in header_lines:

    text = text_of(line)

    upper_text = upper(text)


    if "DUE DATE" in upper_text:

        dates = DATE_PATTERN.findall(
            text
        )

        if dates:

            due_date = dates[0]

            break


# =========================================================
# ORDER NUMBER
# =========================================================

order_number = ""


for line in header_lines:

    text = text_of(line)

    upper_text = upper(text)


    if "ORDER NO" in upper_text:

        # Find the position of Order No

        match = re.search(
            r"ORDER\s*NO\.?",
            text,
            flags=re.IGNORECASE
        )


        if match:

            remaining = text[
                match.end():
            ].strip()


            # Remove common neighboring labels

            remaining = re.split(
                r"\b(CASES|ORDER\s*DATE|DUE\s*DATE|LR|TRANSPORT)\b",
                remaining,
                flags=re.IGNORECASE
            )[0].strip()


            if remaining:

                order_number = remaining


                break


# =========================================================
# LR NUMBER
# =========================================================

lr_number = ""


for line in header_lines:

    text = text_of(line)

    upper_text = upper(text)


    if (
        "LR NO" in upper_text
        or "L.R." in upper_text
    ):

        # Search for a code-like value

        matches = re.findall(
            r"\b[A-Z0-9/-]{6,}\b",
            text,
            flags=re.IGNORECASE
        )


        for candidate in matches:

            candidate_upper = candidate.upper()


            if candidate_upper not in [
                "DUE",
                "DATE",
                "TRANSPORT",
                "LR",
                "NO"
            ]:

                # Avoid date-like values

                if not re.match(
                    r"^\d{2}[-/]\d{2}[-/]\d{4}$",
                    candidate
                ):

                    lr_number = candidate

                    break


        if lr_number:

            break


# =========================================================
# TRANSPORT
# =========================================================

transport = ""


for line in header_lines:

    text = text_of(line)

    upper_text = upper(text)


    if "TRANSPORT" in upper_text:

        # Find word after TRANSPORT

        match = re.search(
            r"TRANSPORT\s*:?\s*([A-Za-z0-9 -]+)",
            text,
            flags=re.IGNORECASE
        )


        if match:

            candidate = match.group(1).strip()


            # Don't capture another field

            candidate = re.split(
                r"\b(LR|DLNO|GSTIN|PHONE|DATE)\b",
                candidate,
                flags=re.IGNORECASE
            )[0].strip()


            transport = candidate

            break


# =========================================================
# WARNINGS
# =========================================================

warnings = []


if not seller_name:

    warnings.append(
        "Seller name not confidently detected"
    )


if not buyer_name:

    warnings.append(
        "Buyer name not confidently detected"
    )


if not seller_gstin:

    warnings.append(
        "Seller GSTIN not confidently detected"
    )


if not buyer_gstin:

    warnings.append(
        "Buyer GSTIN not confidently detected"
    )


if not invoice_number:

    warnings.append(
        "Invoice number not confidently detected"
    )


if not invoice_date:

    warnings.append(
        "Invoice date not confidently detected"
    )


warnings.extend(
    gst_warnings
)


# =========================================================
# FINAL HEADER JSON
# =========================================================

header = {

    "seller": {

        "name": seller_name,

        "phone": seller_phone,

        "gstin": seller_gstin

    },

    "buyer": {

        "name": buyer_name,

        "phone": buyer_phone,

        "gstin": buyer_gstin

    },

    "invoice": {

        "number": invoice_number,

        "date": invoice_date,

        "due_date": due_date,

        "order_number": order_number,

        "lr_number": lr_number,

        "transport": transport

    },

    "warnings": warnings

}


# =========================================================
# SAVE
# =========================================================

with open(
    OUTPUT_FILE,
    "w",
    encoding="utf-8"
) as f:

    json.dump(
        header,
        f,
        indent=4,
        ensure_ascii=False
    )


# =========================================================
# DISPLAY
# =========================================================

print()
print("=" * 70)
print("HEADER EXTRACTION")
print("=" * 70)

print()

print(
    "SELLER:",
    seller_name
)

print(
    "SELLER PHONE:",
    seller_phone
)

print(
    "SELLER GSTIN:",
    seller_gstin
)

print()

print(
    "BUYER:",
    buyer_name
)

print(
    "BUYER PHONE:",
    buyer_phone
)

print(
    "BUYER GSTIN:",
    buyer_gstin
)

print()

print(
    "INVOICE NUMBER:",
    invoice_number
)

print(
    "INVOICE DATE:",
    invoice_date
)

print(
    "DUE DATE:",
    due_date
)

print(
    "ORDER NUMBER:",
    order_number
)

print(
    "LR NUMBER:",
    lr_number
)

print(
    "TRANSPORT:",
    transport
)

if warnings:

    print()
    print("WARNINGS:")

    for warning in warnings:

        print(
            " -",
            warning
        )

print()

print(
    "Saved:",
    OUTPUT_FILE
)

print()
print("=" * 70)
