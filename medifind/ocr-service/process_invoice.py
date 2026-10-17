import json
import subprocess
import sys
import os
from pathlib import Path


# ============================================================
# CONFIGURATION
# ============================================================

BASE_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = Path(os.environ.get("MEDIFIND_OUTPUT_DIR", BASE_DIR))

OCR_SCRIPT = BASE_DIR / "ocr_bill.py"
HEADER_SCRIPT = BASE_DIR / "extract_header.py"
INVOICE_SCRIPT = BASE_DIR / "extract_invoice.py"

HSN_REOCR_SCRIPT = BASE_DIR / "reocr_cells.py"
EXPIRY_REOCR_SCRIPT = BASE_DIR / "reocr_expiry.py"

OCR_JSON = OUTPUT_DIR / "bill_ocr.json"
HEADER_JSON = OUTPUT_DIR / "header.json"
INVOICE_JSON = OUTPUT_DIR / "invoice.json"

HSN_RESULTS_JSON = OUTPUT_DIR / "reocr_results.json"
EXPIRY_RESULTS_JSON = OUTPUT_DIR / "expiry_results.json"

FINAL_JSON = OUTPUT_DIR / "final_invoice.json"


# ============================================================
# GET VALUE
# ============================================================

def get_value(value):

    if isinstance(value, str):
        return value.strip()

    if isinstance(value, dict):

        if "value" in value:
            return str(value["value"]).strip()

        for key in ["text", "name", "raw_value"]:

            if key in value:
                return str(value[key]).strip()

    return ""


# ============================================================
# CLEAN SELLER NAME
# ============================================================

def clean_seller_name(name):

    name = get_value(name)

    if not name:
        return ""

    # Remove obvious OCR garbage
    if name.endswith(" Os"):
        name = name[:-3].strip()

    return name


# ============================================================
# RUN SCRIPT
# ============================================================

def run_script(script_path):

    print("\n" + "=" * 70)
    print(f"RUNNING: {script_path.name}")
    print("=" * 70)

    result = subprocess.run(
        [sys.executable, str(script_path)],
        cwd=str(BASE_DIR)
    )

    if result.returncode != 0:

        print()
        print(f"ERROR: {script_path.name} failed.")

        sys.exit(result.returncode)


# ============================================================
# LOAD JSON
# ============================================================

def load_json(path):

    if not path.exists():

        print()
        print(f"ERROR: Missing file: {path.name}")

        sys.exit(1)

    try:

        with open(
            path,
            "r",
            encoding="utf-8"
        ) as f:

            return json.load(f)

    except json.JSONDecodeError as e:

        print()
        print(f"ERROR: Invalid JSON: {path.name}")
        print(e)

        sys.exit(1)


# ============================================================
# APPLY HSN VALIDATION
# ============================================================

def apply_hsn_validation(items, warnings):

    if not HSN_RESULTS_JSON.exists():

        warnings.append(
            "HSN validation results were not generated"
        )

        return

    hsn_results = load_json(
        HSN_RESULTS_JSON
    )

    if not isinstance(hsn_results, list):
        return

    print()
    print("=" * 70)
    print("APPLYING HSN VALIDATION")
    print("=" * 70)

    for result in hsn_results:

        row = result.get("row")

        if not isinstance(row, int):
            continue

        item_index = row - 1

        if not (
            0 <= item_index < len(items)
        ):
            continue

        item = items[item_index]

        original = get_value(
            result.get("original", "")
        )

        final_value = get_value(
            result.get("final", "")
        )

        status = get_value(
            result.get("status", "")
        )

        # ----------------------------------------------------
        # Re-OCR successfully corrected HSN
        # ----------------------------------------------------

        if status == "reocr_valid":

            if final_value:

                item["hsn"] = final_value

                print(
                    f"Item {row:02}: "
                    f"HSN {original} -> {final_value}"
                )

        # ----------------------------------------------------
        # Original HSN was already valid
        # ----------------------------------------------------

        elif status == "valid":

            if final_value:

                item["hsn"] = final_value

        # ----------------------------------------------------
        # Could not confidently correct
        # ----------------------------------------------------

        elif status == "needs_review":

            if final_value:

                item["hsn"] = final_value

            warning = (
                f"Item {row}: "
                f"HSN needs review; "
                f"OCR value '{final_value or original}' "
                f"was preserved"
            )

            if warning not in warnings:
                warnings.append(warning)

            item_warnings = item.setdefault(
                "warnings",
                []
            )

            item_warning = (
                f"HSN needs review; "
                f"OCR value '{final_value or original}' "
                f"was preserved"
            )

            if item_warning not in item_warnings:

                item_warnings.append(
                    item_warning
                )

    print("HSN validation applied.")


# ============================================================
# APPLY EXPIRY VALIDATION
# ============================================================

def apply_expiry_validation(items, warnings):

    if not EXPIRY_RESULTS_JSON.exists():

        warnings.append(
            "Expiry validation results were not generated"
        )

        return

    expiry_results = load_json(
        EXPIRY_RESULTS_JSON
    )

    if not isinstance(expiry_results, list):
        return

    print()
    print("=" * 70)
    print("APPLYING EXPIRY VALIDATION")
    print("=" * 70)

    for result in expiry_results:

        row = result.get("row")

        if not isinstance(row, int):
            continue

        item_index = row - 1

        if not (
            0 <= item_index < len(items)
        ):
            continue

        item = items[item_index]

        original = get_value(
            result.get("original", "")
        )

        final_value = get_value(
            result.get("final", "")
        )

        status = get_value(
            result.get("status", "")
        )

        # ----------------------------------------------------
        # Re-OCR successfully corrected expiry
        # ----------------------------------------------------

        if status == "reocr_valid":

            if final_value:

                item["expiry"] = final_value

                print(
                    f"Item {row:02}: "
                    f"Expiry {original} -> {final_value}"
                )

        # ----------------------------------------------------
        # Original expiry was valid
        # ----------------------------------------------------

        elif status == "valid":

            if final_value:

                item["expiry"] = final_value

        # ----------------------------------------------------
        # Could not confidently correct
        # ----------------------------------------------------

        elif status == "needs_review":

            if final_value:

                item["expiry"] = final_value

            warning = (
                f"Item {row}: "
                f"Expiry needs review; "
                f"OCR value '{final_value or original}' "
                f"was preserved"
            )

            if warning not in warnings:
                warnings.append(warning)

            item_warnings = item.setdefault(
                "warnings",
                []
            )

            item_warning = (
                f"Expiry needs review; "
                f"OCR value '{final_value or original}' "
                f"was preserved"
            )

            if item_warning not in item_warnings:

                item_warnings.append(
                    item_warning
                )

    print("Expiry validation applied.")


# ============================================================
# BUILD FINAL JSON
# ============================================================

def build_final_json():

    header = load_json(
        HEADER_JSON
    )

    invoice = load_json(
        INVOICE_JSON
    )

    # Prefer a detected payable/grand total. Line amounts commonly sum to the
    # pre-tax subtotal, so replacing an explicit NET PAYABLE with that sum loses
    # GST and other invoice-level charges. Fall back to the row sum only when
    # the OCR stage did not find a usable total.
    item_amounts = []
    for item in invoice.get("items", []) if isinstance(invoice.get("items"), list) else []:
        try:
            item_amounts.append(float(str(item.get("amount", "")).replace(",", "")))
        except (TypeError, ValueError):
            pass
    reconciled_total = round(sum(item_amounts), 2)
    extracted_total = invoice.get("total", 0)
    try:
        extracted_total_number = float(str(extracted_total).replace(",", ""))
    except (TypeError, ValueError):
        extracted_total_number = 0
    total = extracted_total if extracted_total_number > 0 else f"{reconciled_total:.2f}"

    # ========================================================
    # HEADER SECTIONS
    # ========================================================

    seller = header.get(
        "seller",
        {}
    )

    buyer = header.get(
        "buyer",
        {}
    )

    invoice_info = header.get(
        "invoice",
        {}
    )

    if not isinstance(seller, dict):
        seller = {}

    if not isinstance(buyer, dict):
        buyer = {}

    if not isinstance(invoice_info, dict):
        invoice_info = {}

    # ========================================================
    # SELLER
    # ========================================================

    seller_name = clean_seller_name(
        seller.get("name", "")
    )

    seller_phone = get_value(
        seller.get("phone", "")
    )

    seller_gstin = get_value(
        seller.get("gstin", "")
    )

    # ========================================================
    # BUYER
    # ========================================================

    buyer_name = get_value(
        buyer.get("name", "")
    )

    buyer_phone = get_value(
        buyer.get("phone", "")
    )

    buyer_gstin = get_value(
        buyer.get("gstin", "")
    )

    # ========================================================
    # INVOICE
    # ========================================================

    invoice_number = get_value(
        invoice_info.get("number", "")
    )

    invoice_date = get_value(
        invoice_info.get("date", "")
    )

    due_date = get_value(
        invoice_info.get("due_date", "")
    )

    order_number = get_value(
        invoice_info.get("order_number", "")
    )

    lr_number = get_value(
        invoice_info.get("lr_number", "")
    )

    transport = get_value(
        invoice_info.get("transport", "")
    )

    # ========================================================
    # ITEMS
    # ========================================================

    items = invoice.get(
        "items",
        []
    )

    if not isinstance(items, list):
        items = []

    # ========================================================
    # WARNINGS
    # ========================================================

    warnings = []

    header_warnings = header.get(
        "warnings",
        []
    )

    if isinstance(header_warnings, list):

        for warning in header_warnings:

            warning = get_value(warning)

            if warning and warning not in warnings:

                warnings.append(warning)

    # ========================================================
    # BUYER GSTIN
    # ========================================================

    if not buyer_gstin:

        warning = (
            "Buyer GSTIN not confidently detected"
        )

        if warning not in warnings:
            warnings.append(warning)

    # ========================================================
    # APPLY HSN VALIDATION
    # ========================================================

    apply_hsn_validation(
        items,
        warnings
    )

    # ========================================================
    # APPLY EXPIRY VALIDATION
    # ========================================================

    apply_expiry_validation(
        items,
        warnings
    )

    # ========================================================
    # COLLECT EXISTING ITEM WARNINGS
    # ========================================================

    for index, item in enumerate(
        items,
        start=1
    ):

        if not isinstance(item, dict):
            continue

        item_warnings = item.get(
            "warnings",
            []
        )

        if isinstance(
            item_warnings,
            list
        ):

            for warning in item_warnings:

                warning = get_value(warning)

                if not warning:
                    continue

                full_warning = (
                    f"Item {index}: {warning}"
                )

                if full_warning not in warnings:

                    warnings.append(
                        full_warning
                    )

    # ========================================================
    # FINAL DATA
    # ========================================================

    final_data = {

        "invoice": {
            "invoice_number": invoice_number,
            "invoice_date": invoice_date,
            "due_date": due_date,
            "order_number": order_number,
            "lr_number": lr_number,
            "transport": transport
        },

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

        "items": items,

        "total": total,

        "warnings": list(
            dict.fromkeys(warnings)
        )
    }

    # ========================================================
    # SAVE
    # ========================================================

    with open(
        FINAL_JSON,
        "w",
        encoding="utf-8"
    ) as f:

        json.dump(
            final_data,
            f,
            indent=4,
            ensure_ascii=False
        )

    return final_data


# ============================================================
# MAIN
# ============================================================

def main():

    print()
    print("=" * 70)
    print("             INVOICE PROCESSING PIPELINE")
    print("=" * 70)

    # ========================================================
    # 1. OCR
    # ========================================================

    run_script(
        OCR_SCRIPT
    )

    if not OCR_JSON.exists():

        print(
            "\nERROR: bill_ocr.json was not created."
        )

        sys.exit(1)

    # ========================================================
    # 2. HEADER
    # ========================================================

    run_script(
        HEADER_SCRIPT
    )

    if not HEADER_JSON.exists():

        print(
            "\nERROR: header.json was not created."
        )

        sys.exit(1)

    # ========================================================
    # 3. TABLE
    # ========================================================

    run_script(
        INVOICE_SCRIPT
    )

    if not INVOICE_JSON.exists():

        print(
            "\nERROR: invoice.json was not created."
        )

        sys.exit(1)

    # ========================================================
    # 4. HSN TARGETED RE-OCR
    # ========================================================

    run_script(
        HSN_REOCR_SCRIPT
    )

    if not HSN_RESULTS_JSON.exists():

        print(
            "\nWARNING: "
            "reocr_results.json was not created."
        )

    # ========================================================
    # 5. EXPIRY TARGETED RE-OCR
    # ========================================================

    run_script(
        EXPIRY_REOCR_SCRIPT
    )

    if not EXPIRY_RESULTS_JSON.exists():

        print(
            "\nWARNING: "
            "expiry_results.json was not created."
        )

    # ========================================================
    # 6. BUILD FINAL JSON
    # ========================================================

    print()
    print("=" * 70)
    print("BUILDING FINAL INVOICE JSON")
    print("=" * 70)

    final_data = build_final_json()

    # ========================================================
    # SUMMARY
    # ========================================================

    print()
    print("=" * 70)
    print("             PROCESSING COMPLETE")
    print("=" * 70)

    print()
    print(
        "Invoice Number :",
        final_data["invoice"]["invoice_number"]
    )

    print(
        "Invoice Date   :",
        final_data["invoice"]["invoice_date"]
    )

    print(
        "Seller         :",
        final_data["seller"]["name"]
    )

    print(
        "Seller Phone   :",
        final_data["seller"]["phone"]
    )

    print(
        "Seller GSTIN   :",
        final_data["seller"]["gstin"]
    )

    print(
        "Buyer          :",
        final_data["buyer"]["name"]
    )

    print(
        "Buyer Phone    :",
        final_data["buyer"]["phone"]
    )

    print(
        "Items          :",
        len(final_data["items"])
    )

    print(
        "Total          :",
        final_data["total"]
    )

    # ========================================================
    # WARNINGS
    # ========================================================

    print()
    print("Warnings:")

    if final_data["warnings"]:

        for warning in final_data["warnings"]:

            print(
                f" - {warning}"
            )

    else:

        print(" None")

    print()
    print(
        "Saved:",
        FINAL_JSON.name
    )


# ============================================================
# START
# ============================================================

if __name__ == "__main__":

    main()
