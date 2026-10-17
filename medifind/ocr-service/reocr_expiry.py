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
# LOAD OCR
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

                "y": (y1 + y2) / 2

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
    # Match the main invoice extractor: docTR may split the printed heading
    # into nearby fragments.  Expiry re-OCR still operates on its original
    # fixed expiry crop for each detected table row.
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
# COLUMN POSITIONS
# =========================================================

COLUMNS = {

    "batch": 0.4668,

    "expiry": 0.5239,

    "hsn": 0.5610

}


# =========================================================
# EXPIRY COLUMN BOUNDARIES
# =========================================================

expiry_left = (

    COLUMNS["batch"]
    +
    COLUMNS["expiry"]

) / 2


expiry_right = (

    COLUMNS["expiry"]
    +
    COLUMNS["hsn"]

) / 2


print()
print(
    "EXPIRY X RANGE:",
    expiry_left,
    "to",
    expiry_right
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


    if (
        "PRODUCT" in text.upper()
        and
        "BATCH" in text.upper()
    ):

        continue


    table_lines.append(line)


# =========================================================
# GET EXPIRY VALUES
# =========================================================

rows = []


for line in table_lines:

    expiry_words = []


    for word in line["words"]:

        if (
            expiry_left
            <= word["x"]
            <= expiry_right
        ):

            expiry_words.append(
                word
            )


    if not expiry_words:

        continue


    expiry_words.sort(
        key=lambda w: w["x"]
    )


    expiry_text = " ".join(
        w["text"]
        for w in expiry_words
    )


    if not expiry_text:

        continue


    rows.append({

        "y": line["y"],

        "original": expiry_text

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
    "EXPIRY ROWS FOUND:",
    len(rows)
)


# =========================================================
# EXPIRY VALIDATION
# =========================================================

def normalize_expiry(text):

    text = text.strip().upper()


    # Remove spaces

    text = re.sub(
        r"\s+",
        "",
        text
    )


    # Common OCR issue:
    #
    # 228 -> 2/28
    #
    # But DON'T automatically convert it.
    #
    # We only identify it as suspicious.

    return text


def is_valid_expiry(text):

    text = normalize_expiry(
        text
    )


    # M/YY

    if re.fullmatch(
        r"[1-9]/\d{2}",
        text
    ):

        return True


    # MM/YY

    if re.fullmatch(
        r"(0[1-9]|1[0-2])/\d{2}",
        text
    ):

        return True


    return False


# =========================================================
# LOAD MODEL ONLY IF NEEDED
# =========================================================

model = None


def get_model():

    global model


    if model is None:

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


    return model


# =========================================================
# TARGETED RE-OCR
# =========================================================

results = []


print()
print("=" * 70)
print("EXPIRY VALIDATION")
print("=" * 70)


for index, row in enumerate(
    rows,
    start=1
):

    original = row["original"]


    # =====================================================
    # VALID ORIGINAL
    # =====================================================

    if is_valid_expiry(
        original
    ):

        print()

        print(
            f"{index:02}. "
            f"{original} "
            f"-> VALID, no re-OCR needed"
        )


        results.append({

            "row": index,

            "original": original,

            "final": original,

            "method": "original_ocr",

            "status": "valid"

        })


        continue


    # =====================================================
    # SUSPICIOUS
    # =====================================================

    print()

    print(
        f"{index:02}. "
        f"{original} "
        f"-> SUSPICIOUS, re-OCR"
    )


    # -----------------------------------------------------
    # X COORDINATES
    # -----------------------------------------------------

    x1 = int(
        expiry_left
        * image_width
    )


    x2 = int(
        expiry_right
        * image_width
    )


    # -----------------------------------------------------
    # Y
    # -----------------------------------------------------

    center_y = int(
        row["y"]
        * image_height
    )


    # -----------------------------------------------------
    # VERY TIGHT CROP
    # -----------------------------------------------------

    y1 = max(
        0,
        center_y - 9
    )


    y2 = min(
        image_height,
        center_y + 9
    )


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

        results.append({

            "row": index,

            "original": original,

            "final": original,

            "method": "original_ocr",

            "status": "needs_review"

        })

        continue


    # =====================================================
    # UPSCALE
    # =====================================================

    crop = cv2.resize(

        crop,

        None,

        fx=5,

        fy=5,

        interpolation=cv2.INTER_CUBIC

    )


    # =====================================================
    # GRAYSCALE
    # =====================================================

    gray = cv2.cvtColor(

        crop,

        cv2.COLOR_BGR2GRAY

    )


    # =====================================================
    # THRESHOLD
    # =====================================================

    processed = cv2.threshold(

        gray,

        0,

        255,

        cv2.THRESH_BINARY
        +
        cv2.THRESH_OTSU

    )[1]


    # =====================================================
    # SAVE DEBUG IMAGE
    # =====================================================

    crop_file = (
        f"expiry_suspicious_{index}.png"
    )


    cv2.imwrite(
        crop_file,
        processed
    )


    # =====================================================
    # OCR
    # =====================================================

    model_instance = get_model()


    doc = DocumentFile.from_images(
        crop_file
    )


    result = model_instance(
        doc
    )


    detected = []


    for result_page in result.pages:

        for block in result_page.blocks:

            for line in block.lines:

                for word in line.words:

                    detected.append(
                        word.value
                    )


    reocr_text = " ".join(
        detected
    )


    print(
        "    Re-OCR:",
        reocr_text
    )


    # =====================================================
    # SEARCH FOR EXPIRY PATTERN
    # =====================================================

    candidates = re.findall(

        r"\b(?:0?[1-9]|1[0-2])\s*/\s*\d{2}\b",

        reocr_text

    )


    # Normalize candidates

    normalized_candidates = []


    for candidate in candidates:

        candidate = re.sub(
            r"\s+",
            "",
            candidate
        )


        if candidate not in normalized_candidates:

            normalized_candidates.append(
                candidate
            )


    print(
        "    Candidates:",
        normalized_candidates
    )


    # =====================================================
    # DECISION
    # =====================================================

    if normalized_candidates:

        final_value = (
            normalized_candidates[0]
        )

        status = "reocr_valid"

        method = "targeted_reocr"


    else:

        final_value = original

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

        "candidates":
            normalized_candidates,

        "method": method,

        "status": status

    })


# =========================================================
# SAVE
# =========================================================

with open(
    "expiry_results.json",
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
print("EXPIRY VALIDATION COMPLETE")
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
    "Saved: expiry_results.json"
)

print()
print("=" * 70)
