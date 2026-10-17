import json
import re
import cv2
import os

from doctr.io import DocumentFile
from doctr.models import ocr_predictor


# =========================================================
# FILES
# =========================================================

OCR_FILE = os.environ.get("MEDIFIND_OCR_JSON", "bill_ocr.json")

IMAGE_FILE = os.environ.get("MEDIFIND_SCANNED_IMAGE", (
    r"C:\Users\abhir\Downloads"
    r"\OpenCV-Document-Scanner-master"
    r"\OpenCV-Document-Scanner-master"
    r"\output\bill_image.jpeg"
))


# =========================================================
# LOAD OCR JSON
# =========================================================

with open(
    OCR_FILE,
    "r",
    encoding="utf-8"
) as f:

    ocr_data = json.load(f)


page = ocr_data["pages"][0]


# =========================================================
# LOAD IMAGE
# =========================================================

image = cv2.imread(
    IMAGE_FILE
)


if image is None:

    raise RuntimeError(
        "Could not load image:\n"
        + IMAGE_FILE
    )


image_height, image_width = image.shape[:2]


print(
    "IMAGE SIZE:",
    image_width,
    "x",
    image_height
)


# =========================================================
# LOAD WORDS
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

                "confidence":
                    word.get("confidence")

            })


# =========================================================
# GROUP WORDS INTO LINES
# =========================================================

words.sort(
    key=lambda w: (w["y"], w["x"])
)


lines = []


Y_THRESHOLD = 0.018


for word in words:

    found = False


    for line in lines:

        if abs(
            word["y"]
            -
            line["y"]
        ) < Y_THRESHOLD:

            line["words"].append(
                word
            )

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
# HELPER
# =========================================================

def line_text(line):

    return " ".join(
        w["text"]
        for w in line["words"]
    )


# =========================================================
# FIND TABLE HEADER
# =========================================================

header_line = None
header_y = None


for line in lines:

    text = line_text(line).upper()


    score = 0


    for key in [
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

        if key in text:

            score += 1


    if score >= 6:

        header_line = line
        header_y = line["y"]

        break


if header_line is None:
    # The document OCR can split this narrow, multi-column header across
    # several lines.  Keep targeted HSN re-OCR intact and use the bottom of
    # that header band as the start of the table.
    header_keys = ["QTY", "MFR", "PACK", "PRODUCT", "BATCH", "EXP", "HSN", "RATE", "AMOUNT", "SGST", "CGST"]
    fragments = [line for line in lines if any(key in line_text(line).upper() for key in header_keys)]
    if not fragments:
        raise RuntimeError("Could not find table header")
    first_y = min(line["y"] for line in fragments)
    header_band = [line for line in fragments if line["y"] <= first_y + 0.055]
    header_y = max(line["y"] for line in header_band)
    header_line = {
        "y": header_y,
        "words": sorted([word for line in header_band for word in line["words"]], key=lambda word: word["x"])
    }


print()
print(
    "TABLE HEADER:"
)

print(
    line_text(header_line)
)

print(
    "HEADER Y:",
    header_y
)


# =========================================================
# FIND TOTAL
# =========================================================

total_y = 0.90


for line in lines:

    text = line_text(line).upper()


    if "TOTAL" in text:

        total_y = line["y"]

        break


# =========================================================
# COLUMN POSITIONS
# =========================================================
#
# Taken from your successfully working extractor.
# =========================================================

COLUMNS = {

    "expiry": 0.5239,

    "hsn": 0.5610,

    "mrp": 0.6240

}


# HSN starts halfway between Expiry and HSN

hsn_left = (
    COLUMNS["expiry"]
    +
    COLUMNS["hsn"]
) / 2


# HSN ends halfway between HSN and MRP

hsn_right = (
    COLUMNS["hsn"]
    +
    COLUMNS["mrp"]
) / 2


print()
print(
    "HSN X RANGE:",
    hsn_left,
    "to",
    hsn_right
)


# =========================================================
# FIND TABLE ROWS
# =========================================================

table_lines = []


for line in lines:

    y = line["y"]


    if not (
        header_y
        <
        y
        <
        total_y
    ):

        continue


    text = line_text(line).strip()


    if not text:

        continue


    upper = text.upper()


    # Skip footer/header-like lines

    if (
        "PRODUCT" in upper
        and "BATCH" in upper
    ):

        continue


    table_lines.append(line)


# =========================================================
# GET ORIGINAL HSN FOR EACH ROW
# =========================================================

rows = []


for line in table_lines:

    hsn_words = []


    for word in line["words"]:

        x = word["x"]


        if (
            hsn_left
            <= x
            <= hsn_right
        ):

            hsn_words.append(
                word
            )


    if not hsn_words:

        continue


    hsn_words.sort(
        key=lambda w: w["x"]
    )


    hsn_text = " ".join(
        w["text"]
        for w in hsn_words
    )


    hsn_digits = re.sub(
        r"\D",
        "",
        hsn_text
    )


    if not hsn_digits:

        continue


    rows.append({

        "y": line["y"],

        "original": hsn_text,

        "digits": hsn_digits

    })


# =========================================================
# REMOVE DUPLICATES
# =========================================================

unique_rows = []


for row in rows:

    duplicate = False


    for existing in unique_rows:

        if abs(
            row["y"]
            -
            existing["y"]
        ) < 0.012:

            duplicate = True

            break


    if not duplicate:

        unique_rows.append(
            row
        )


rows = unique_rows


print()
print(
    "HSN ROWS FOUND:",
    len(rows)
)


# =========================================================
# LOAD OCR MODEL
# =========================================================

print()
print(
    "Loading docTR model..."
)


model = ocr_predictor(

    det_arch="db_resnet50",

    reco_arch="crnn_vgg16_bn",

    pretrained=True

)


print(
    "Model loaded."
)


# =========================================================
# TARGETED RE-OCR
# =========================================================

results = []


print()
print("=" * 70)
print("TARGETED HSN VALIDATION")
print("=" * 70)


for index, row in enumerate(
    rows,
    start=1
):

    original = row["original"]

    original_digits = row["digits"]


    # -----------------------------------------------------
    # IMPORTANT:
    #
    # If already exactly 8 digits, TRUST IT.
    # -----------------------------------------------------

    if len(original_digits) == 8:

        print()

        print(
            f"{index:02}. "
            f"{original_digits} "
            f"-> VALID, no re-OCR needed"
        )


        results.append({

            "row": index,

            "original": original,

            "final": original_digits,

            "method": "original_ocr",

            "status": "valid"

        })


        continue


    # -----------------------------------------------------
    # Suspicious HSN
    # -----------------------------------------------------

    print()

    print(
        f"{index:02}. "
        f"{original_digits} "
        f"-> SUSPICIOUS, re-OCR"
    )


    # -----------------------------------------------------
    # IMAGE COORDINATES
    # -----------------------------------------------------

    x1 = int(
        hsn_left
        * image_width
    )


    x2 = int(
        hsn_right
        * image_width
    )


    center_y = int(
        row["y"]
        * image_height
    )


    # -----------------------------------------------------
    # VERY TIGHT VERTICAL CROP
    #
    # Previous version used ±22 pixels.
    #
    # That captured neighboring rows.
    #
    # Now use ±9 pixels.
    # -----------------------------------------------------

    y1 = max(
        0,
        center_y - 9
    )


    y2 = min(
        image_height,
        center_y + 9
    )


    # Small horizontal padding

    x1 = max(
        0,
        x1 - 3
    )


    x2 = min(
        image_width,
        x2 + 3
    )


    crop = image[
        y1:y2,
        x1:x2
    ]


    if crop.size == 0:

        print(
            "    Crop failed"
        )


        results.append({

            "row": index,

            "original": original,

            "final": original_digits,

            "method": "original_ocr",

            "status": "reocr_failed"

        })


        continue


    # -----------------------------------------------------
    # UPSCALE
    # -----------------------------------------------------

    crop = cv2.resize(

        crop,

        None,

        fx=5,

        fy=5,

        interpolation=cv2.INTER_CUBIC

    )


    # -----------------------------------------------------
    # GRAYSCALE
    # -----------------------------------------------------

    gray = cv2.cvtColor(

        crop,

        cv2.COLOR_BGR2GRAY

    )


    # -----------------------------------------------------
    # THRESHOLD
    # -----------------------------------------------------

    processed = cv2.threshold(

        gray,

        0,

        255,

        cv2.THRESH_BINARY
        +
        cv2.THRESH_OTSU

    )[1]


    # -----------------------------------------------------
    # SAVE DEBUG CROP
    # -----------------------------------------------------

    crop_file = (
        f"hsn_suspicious_{index}.png"
    )


    cv2.imwrite(
        crop_file,
        processed
    )


    # -----------------------------------------------------
    # OCR
    # -----------------------------------------------------

    doc = DocumentFile.from_images(
        crop_file
    )


    result = model(
        doc
    )


    detected_words = []


    for result_page in result.pages:

        for block in result_page.blocks:

            for line in block.lines:

                for word in line.words:

                    detected_words.append(
                        word.value
                    )


    reocr_text = " ".join(
        detected_words
    )


    # -----------------------------------------------------
    # Extract possible 8-digit HSNs
    # -----------------------------------------------------

    candidates = re.findall(
        r"\d{8}",
        reocr_text
    )


    print(
        "    Re-OCR:",
        reocr_text
    )


    print(
        "    Candidates:",
        candidates
    )


    # -----------------------------------------------------
    # DECISION
    # -----------------------------------------------------

    if candidates:

        # Prefer first valid 8-digit candidate

        final_value = candidates[0]

        status = "reocr_valid"

        method = "targeted_reocr"


    else:

        # IMPORTANT:
        # Don't invent a value.
        #
        # Keep original OCR.

        final_value = original_digits

        status = "needs_review"

        method = "original_ocr"


    print(
        "    FINAL:",
        final_value
    )


    results.append({

        "row": index,

        "original": original,

        "final": final_value,

        "reocr": reocr_text,

        "candidates": candidates,

        "method": method,

        "status": status

    })


# =========================================================
# SAVE RESULTS
# =========================================================

with open(
    "reocr_results.json",
    "w",
    encoding="utf-8"
) as f:

    json.dump(

        results,

        f,

        indent=4,

        ensure_ascii=False

    )


# =========================================================
# SUMMARY
# =========================================================

print()
print("=" * 70)
print("HSN VALIDATION COMPLETE")
print("=" * 70)

print()


for result in results:

    print(

        f"Row {result['row']:02}: "

        f"{result['original']} "

        f"-> "

        f"{result['final']} "

        f"[{result['status']}]"

    )


print()

print(
    "Saved: reocr_results.json"
)

print()
print("=" * 70)
