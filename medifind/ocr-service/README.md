# MediFind bill OCR service

Pipeline: `photo -> OpenCV document detection and perspective correction -> docTR OCR -> JSON word boxes`.

Run it with Python 3.11 or 3.12 (rather than Python 3.13):

```powershell
cd medifind/ocr-service
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app:app --host 127.0.0.1 --port 8002
```

The Spring backend sends the cropped/enhanced bill to `POST /ocr`. Its response contains `text`, full docTR `document`, flattened `words` with `geometry`, and `perspectiveCorrected`.
