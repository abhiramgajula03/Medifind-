from doctr.io import DocumentFile
from doctr.models import ocr_predictor
import json
import os

# Load OCR model
model = ocr_predictor(det_arch="db_resnet50", reco_arch="crnn_vgg16_bn", pretrained=True)

# Load the processed bill from OpenCV
doc = DocumentFile.from_images(os.environ.get("MEDIFIND_SCANNED_IMAGE", r"C:\Users\abhir\Downloads\OpenCV-Document-Scanner-master\OpenCV-Document-Scanner-master\output\bill_image.jpeg"))

# Run OCR
result = model(doc)

# Export docTR result to Python dictionary
ocr_data = result.export()

# Save as JSON
with open(os.environ.get("MEDIFIND_OCR_JSON", "bill_ocr.json"), "w", encoding="utf-8") as f:
    json.dump(ocr_data, f, indent=4, ensure_ascii=False)

print("OCR completed successfully!")
print("Saved as bill_ocr.json")
