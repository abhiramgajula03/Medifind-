import json
import os
import runpy
import sys
import threading
import uuid
import logging
from pathlib import Path
import cv2
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from doctr.models import ocr_predictor

ROOT = Path(__file__).resolve().parent
TEMP = ROOT / "temp"
MODEL = None
PIPELINE_LOCK = threading.Lock()
app = FastAPI(title="MediFind Invoice OCR")
logger = logging.getLogger("medifind.ocr")


def image_details(path: Path) -> dict:
    """Return evidence about an image without altering its bytes."""
    image = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
    if image is None:
        raise ValueError(f"Image is unreadable: {path}")
    return {
        "path": str(path),
        "exists": path.exists(),
        "bytes": path.stat().st_size,
        "width": int(image.shape[1]),
        "height": int(image.shape[0]),
        "channels": 1 if image.ndim == 2 else int(image.shape[2]),
        "format": path.suffix.lstrip(".").upper(),
    }


def write_diagnostics(workdir: Path, diagnostics: dict) -> None:
    (workdir / "diagnostics.json").write_text(
        json.dumps(diagnostics, indent=2), encoding="utf-8"
    )

def ocr_layout_counts(path: Path) -> dict:
    """Count docTR layout objects for diagnostics only; this does not alter extraction."""
    payload = json.loads(path.read_text(encoding="utf-8"))
    lines, words, positions = 0, 0, []
    for page in payload.get("pages", []):
        for block in page.get("blocks", []):
            for line in block.get("lines", []):
                lines += 1
                words += len(line.get("words", []))
                geometry = line.get("geometry", [])
                if geometry and geometry[0]: positions.append(round(float(geometry[0][1]), 4))
    return {"docTR_line_count": lines, "docTR_word_count": words, "docTR_line_y_positions": positions}

@app.on_event("startup")
def load_model():
    global MODEL
    TEMP.mkdir(exist_ok=True)
    MODEL = ocr_predictor(det_arch="db_resnet50", reco_arch="crnn_vgg16_bn", pretrained=True)

def scan_bill(source: Path, destination: Path, diagnostics: dict):
    scanner_root = ROOT / "opencv-scanner"
    sys.path.insert(0, str(scanner_root))
    from scan import DocScanner
    from pyimagesearch import imutils, transform
    image = cv2.imread(str(source))
    if image is None: raise ValueError("Upload is not a readable image")
    ratio = image.shape[0] / 500.0
    resized = imutils.resize(image, height=500)
    contour = DocScanner(interactive=False).get_contour(resized)

    # The tested scanner uses the whole image when it cannot obtain a valid
    # document quadrilateral. A crop already contains the invoice, and the
    # scanner can occasionally select intersecting table lines as a *non*
    # convex quadrilateral. Such a contour produces a corrupted perspective
    # transform. Treat it as an invalid scanner result and use the scanner's
    # own full-frame fallback; do not change any OCR or table extraction rule.
    contour_int = contour.astype("int32")
    if not cv2.isContourConvex(contour_int.reshape((-1, 1, 2))):
        diagnostics["scanner"] = {
            "contour_scaled": contour.tolist(),
            "used_full_frame_fallback": True,
            "fallback_reason": "scanner returned a non-convex quadrilateral",
        }
        height, width = resized.shape[:2]
        contour = np.array(
            [[width, 0], [width, height], [0, height], [0, 0]], dtype="float32"
        )
    else:
        diagnostics["scanner"] = {
            "contour_scaled": contour.tolist(),
            "used_full_frame_fallback": False,
        }
    warped = transform.four_point_transform(image, contour * ratio)
    gray = cv2.cvtColor(warped, cv2.COLOR_BGR2GRAY)
    sharpen = cv2.addWeighted(gray, 1.5, cv2.GaussianBlur(gray, (0, 0), 3), -0.5, 0)
    if not cv2.imwrite(str(destination), cv2.adaptiveThreshold(sharpen, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 21, 15)):
        raise RuntimeError(f"Could not write scanned image: {destination}")
    diagnostics["scanned_image"] = image_details(destination)

def execute_pipeline(workdir: Path, diagnostics: dict):
    paths = {"MEDIFIND_OUTPUT_DIR": str(workdir), "MEDIFIND_SCANNED_IMAGE": str(workdir / "scanned.jpeg"), "MEDIFIND_OCR_JSON": str(workdir / "bill_ocr.json"), "MEDIFIND_HEADER_JSON": str(workdir / "header.json"), "MEDIFIND_INVOICE_JSON": str(workdir / "invoice.json")}
    old_env, old_cwd = dict(os.environ), os.getcwd()
    import doctr.models
    factory = doctr.models.ocr_predictor
    try:
        os.environ.update(paths); os.chdir(workdir)
        doctr.models.ocr_predictor = lambda *args, **kwargs: MODEL
        diagnostics["stages"] = []
        for script in ("ocr_bill.py", "extract_header.py", "extract_invoice.py", "reocr_cells.py", "reocr_expiry.py"):
            diagnostics["current_stage"] = script
            logger.info("Request %s: running %s", diagnostics["request_id"], script)
            stage_namespace = runpy.run_path(str(ROOT / script), run_name=f"pipeline_{script}")
            diagnostics["stages"].append({"script": script, "status": "completed"})
            if script == "extract_invoice.py":
                diagnostics["table_header_detection_result"] = "found; invoice.json was generated"
                invoice_payload = json.loads((workdir / "invoice.json").read_text(encoding="utf-8"))
                diagnostics["extraction"] = {
                    "item_row_count": len(invoice_payload.get("items", [])),
                    "items": [{"index": index + 1, "product": item.get("product", "")}
                              for index, item in enumerate(invoice_payload.get("items", []))],
                    "item_row_positions": [
                        {"index": index + 1, "y": round(float(line.get("y", 0)), 4),
                         "text": " ".join(word.get("text", "") for word in line.get("words", []))[:240]}
                        for index, line in enumerate(stage_namespace.get("item_lines", []))
                    ],
                }
            write_diagnostics(workdir, diagnostics)
        diagnostics["current_stage"] = "process_invoice.py"
        result = runpy.run_path(str(ROOT / "process_invoice.py"), run_name="pipeline_merge")["build_final_json"]()
        diagnostics["stages"].append({"script": "process_invoice.py", "status": "completed"})
        diagnostics["ocr_layout"] = ocr_layout_counts(workdir / "bill_ocr.json")
        diagnostics["final_item_count"] = len(result.get("items", []))
        diagnostics["final_items"] = [{"index": index + 1, "product": item.get("product", "")}
                                      for index, item in enumerate(result.get("items", []))]
        extracted_count = diagnostics.get("extraction", {}).get("item_row_count")
        if extracted_count is not None and extracted_count != diagnostics["final_item_count"]:
            result.setdefault("warnings", []).append(
                f"Extraction produced {extracted_count} item rows but final output contains {diagnostics['final_item_count']}; review diagnostics.json."
            )
            (workdir / "final_invoice.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        diagnostics["pipeline_completed"] = True
        return result
    finally:
        doctr.models.ocr_predictor = factory
        os.chdir(old_cwd); os.environ.clear(); os.environ.update(old_env)

@app.get("/health")
def health(): return {"status": "ok", "modelLoaded": MODEL is not None}

@app.post("/ocr/invoice")
async def ocr_invoice(file: UploadFile = File(...),
                      original_image: UploadFile | None = File(None),
                      rotated_image: UploadFile | None = File(None),
                      client_metadata: str | None = Form(None)):
    if not (file.content_type or "").startswith("image/"): raise HTTPException(400, "Upload an image file.")
    request_id = str(uuid.uuid4())
    workdir = TEMP / request_id; workdir.mkdir(parents=True)
    diagnostics = {"request_id": request_id, "upload": {"filename": file.filename, "content_type": file.content_type}}
    if client_metadata:
        try: diagnostics["frontend"] = json.loads(client_metadata)
        except json.JSONDecodeError: diagnostics["frontend"] = {"metadata_error": "invalid client metadata"}
    # The browser has already limited this upload to the user-selected bill
    # region. Preserve exactly those received bytes. The OpenCV scanner always
    # consumes frontend-cropped.jpeg, never a hard-coded sample image.
    payload = await file.read()
    if not payload:
        raise HTTPException(400, "Uploaded image is empty.")
    if len(payload) < 1024:
        raise HTTPException(400, "Uploaded image is too small to contain a bill.")
    original_payload = await original_image.read() if original_image else payload
    rotated_payload = await rotated_image.read() if rotated_image else original_payload
    original = workdir / "original.jpeg"; original.write_bytes(original_payload)
    rotated = workdir / "rotated.jpeg"; rotated.write_bytes(rotated_payload)
    frontend_cropped = workdir / "frontend-cropped.jpeg"; frontend_cropped.write_bytes(payload)
    diagnostics["original_image"] = image_details(original)
    diagnostics["rotated_image"] = image_details(rotated)
    diagnostics["frontend_cropped_image"] = image_details(frontend_cropped)
    if diagnostics["frontend_cropped_image"]["width"] < 300 or diagnostics["frontend_cropped_image"]["height"] < 300:
        raise HTTPException(400, "Cropped image is too small. Include the complete invoice and try again.")
    diagnostics["paths"] = {
        "scanner_input": str(frontend_cropped),
        "original": str(original),
        "rotated": str(rotated),
        "doctr_input": str(workdir / "scanned.jpeg"),
        "ocr_json": str(workdir / "bill_ocr.json"),
        "header_json": str(workdir / "header.json"),
        "invoice_json": str(workdir / "invoice.json"),
        "reocr_results_json": str(workdir / "reocr_results.json"),
        "expiry_results_json": str(workdir / "expiry_results.json"),
        "final_invoice_json": str(workdir / "final_invoice.json"),
    }
    write_diagnostics(workdir, diagnostics)
    try:
        with PIPELINE_LOCK:
            logger.info("Request %s: scanner input %s", request_id, frontend_cropped)
            scan_bill(frontend_cropped, workdir / "scanned.jpeg", diagnostics)
            logger.info("Request %s: docTR input %s", request_id, workdir / "scanned.jpeg")
            write_diagnostics(workdir, diagnostics)
            result = execute_pipeline(workdir, diagnostics)
            write_diagnostics(workdir, diagnostics)
            return result
    except ValueError as error: raise HTTPException(400, str(error)) from error
    except Exception as error:
        diagnostics["pipeline_completed"] = False
        diagnostics["error"] = str(error)
        if diagnostics.get("current_stage") == "extract_invoice.py":
            diagnostics["table_header_detection_result"] = "not found"
        write_diagnostics(workdir, diagnostics)
        logger.exception("Invoice OCR failed in request directory %s", workdir)
        raise HTTPException(500, f"Invoice OCR failed: {error}") from error
