import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { scanBill, persistOcrInvoice, registerShopAgency, getBillScans, getAgencies, getAgencyBills, getShopAgencies, getInvoices } from '../services/api';

export default function BillScanner() {
  const shopId = localStorage.getItem('shopId');
  const navigate = useNavigate();
  const [allAgencies, setAllAgencies] = useState([]);
  const [myAgencies, setMyAgencies] = useState([]);
  const [scans, setScans] = useState([]);
  const [agencyBills, setAgencyBills] = useState([]);
  const [invoices, setInvoices] = useState([]);
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [agencyId, setAgencyId] = useState('');
  const [agencyName, setAgencyName] = useState('');
  const [agencyResolution, setAgencyResolution] = useState(null);
  const [agencyDraft, setAgencyDraft] = useState('');
  const [addingAgency, setAddingAgency] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [activeTab, setActiveTab] = useState('scan');
  const [processing, setProcessing] = useState(false);
  const [detectedBill, setDetectedBill] = useState(null);
  const [extractedData, setExtractedData] = useState(null);
  const fileInputRef = useRef(null);
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const [cameraActive, setCameraActive] = useState(false);
  const [cropSource, setCropSource] = useState(null);
  const [cropFile, setCropFile] = useState(null);
  const [originalUpload, setOriginalUpload] = useState(null);
  const [cropConfirmation, setCropConfirmation] = useState(null);
  const [cropZoom, setCropZoom] = useState(1);
  const [manualRotation, setManualRotation] = useState(0);
  const [imageDebug, setImageDebug] = useState({});
  const [cropRect, setCropRect] = useState({ x: 0, y: 0, width: 100, height: 100 });
  const [cropDrag, setCropDrag] = useState(null);
  const cropFrameRef = useRef(null);
  const cropImageRef = useRef(null);

  useEffect(() => {
    loadData();
  }, [shopId]);

  const loadData = async () => {
    if (!shopId) return;
    const results = await Promise.allSettled([
        getAgencies(),
        getShopAgencies(shopId),
        getBillScans(shopId),
        getAgencyBills(shopId),
        getInvoices(shopId)
    ]);
    const [agencies, linkedAgencies, scanHistory, agencyBillHistory, invoiceHistory] = results;

    if (agencies.status === 'fulfilled') setAllAgencies(agencies.value.data || []);
    if (linkedAgencies.status === 'fulfilled') {
      setMyAgencies((linkedAgencies.value.data || []).filter(agency => agency.isActive !== false));
    }
    if (scanHistory.status === 'fulfilled') setScans(scanHistory.value.data || []);
    if (agencyBillHistory.status === 'fulfilled') setAgencyBills(agencyBillHistory.value.data || []);
    if (invoiceHistory.status === 'fulfilled') setInvoices(invoiceHistory.value.data || []);

    if (results.some(result => result.status === 'rejected')) {
      console.error('One or more scanner history requests failed:', results);
      setMessage(previous => previous || 'Some history data could not be refreshed. Your saved invoice and inventory are still available.');
    }
  };

  const startCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        setCameraActive(true);
      }
    } catch (err) {
      setMessage('Could not access camera. Please upload an image instead.');
    }
  };

  const stopCamera = () => {
    if (videoRef.current && videoRef.current.srcObject) {
      videoRef.current.srcObject.getTracks().forEach(track => track.stop());
      videoRef.current.srcObject = null;
      setCameraActive(false);
    }
  };

  const capturePhoto = () => {
    if (!videoRef.current || !canvasRef.current) return;
    const video = videoRef.current;
    const canvas = canvasRef.current;
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0);
    canvas.toBlob((blob) => {
      if (!blob) {
        setMessage('Could not capture bill image. Please try again.');
        return;
      }
      openCropper(new File([blob], `bill-${Date.now()}.jpg`, { type: 'image/jpeg' }));
    }, 'image/jpeg', 0.98);
    stopCamera();
  };

  const makeCanvasFile = (canvas, filename) => new Promise((resolve, reject) => {
    canvas.toBlob((blob) => blob ? resolve(new File([blob], filename, { type: 'image/jpeg' })) : reject(new Error('Could not create image.')), 'image/jpeg', 0.98);
  });

  // createImageBitmap applies phone EXIF orientation before any crop math is calculated.
  const normalizeImage = async (selected) => {
    const bitmap = await createImageBitmap(selected, { imageOrientation: 'from-image' });
    const canvas = document.createElement('canvas');
    canvas.width = bitmap.width; canvas.height = bitmap.height;
    canvas.getContext('2d').drawImage(bitmap, 0, 0);
    bitmap.close?.();
    return { file: await makeCanvasFile(canvas, `normalized-bill-${Date.now()}.jpg`), width: canvas.width, height: canvas.height };
  };

  const openCropper = async (selected) => {
    setFile(null);
    setPreview(null);
    setOriginalUpload(selected);
    try {
      const normalized = await normalizeImage(selected);
      setCropFile(normalized.file);
      setCropRect({ x: 0, y: 0, width: 100, height: 100 });
      setCropZoom(1); setManualRotation(0);
      setImageDebug({ original: { bytes: selected.size }, normalized: { width: normalized.width, height: normalized.height, bytes: normalized.file.size } });
      setCropSource(URL.createObjectURL(normalized.file));
    } catch {
      setMessage('Could not read this image. Please choose a valid photo.');
    }
  };

  const handleFileChange = (e) => {
    const selected = e.target.files[0];
    if (!selected) return;
    openCropper(selected);
    e.target.value = '';
  };

  const cropPointerPosition = (event) => {
    const bounds = cropFrameRef.current?.getBoundingClientRect();
    if (!bounds) return null;
    return {
      x: Math.max(0, Math.min(100, ((event.clientX - bounds.left) / bounds.width) * 100)),
      y: Math.max(0, Math.min(100, ((event.clientY - bounds.top) / bounds.height) * 100))
    };
  };

  const beginCrop = (event) => {
    const point = cropPointerPosition(event);
    if (!point) return;
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setCropDrag({ startX: point.x, startY: point.y });
    setCropRect({ x: point.x, y: point.y, width: 0, height: 0 });
  };

  const moveCrop = (event) => {
    if (!cropDrag) return;
    const point = cropPointerPosition(event);
    if (!point) return;
    setCropRect({
      x: Math.min(cropDrag.startX, point.x), y: Math.min(cropDrag.startY, point.y),
      width: Math.abs(point.x - cropDrag.startX), height: Math.abs(point.y - cropDrag.startY)
    });
  };

  const finishCropSelection = () => setCropDrag(null);

  const rotateCropImage = async () => {
    const image = cropImageRef.current;
    if (!image) return;
    const canvas = document.createElement('canvas');
    canvas.width = image.naturalHeight; canvas.height = image.naturalWidth;
    const context = canvas.getContext('2d');
    context.translate(canvas.width, 0); context.rotate(Math.PI / 2);
    context.drawImage(image, 0, 0);
    try {
      const rotated = await makeCanvasFile(canvas, `rotated-bill-${Date.now()}.jpg`);
      URL.revokeObjectURL(cropSource);
      setCropFile(rotated); setCropSource(URL.createObjectURL(rotated));
      setManualRotation((value) => (value + 90) % 360);
      setCropRect({ x: 0, y: 0, width: 100, height: 100 });
      setImageDebug((debug) => ({ ...debug, rotated: { width: canvas.width, height: canvas.height, bytes: rotated.size } }));
    } catch { setMessage('Could not rotate the image.'); }
  };

  const createCroppedFile = () => {
    const image = cropImageRef.current;
    if (!image || cropRect.width < 5 || cropRect.height < 5) {
      setMessage('Draw a crop rectangle around the invoice before continuing.');
      return;
    }
    const sourceX = Math.round((cropRect.x / 100) * image.naturalWidth);
    const sourceY = Math.round((cropRect.y / 100) * image.naturalHeight);
    const sourceWidth = Math.round((cropRect.width / 100) * image.naturalWidth);
    const sourceHeight = Math.round((cropRect.height / 100) * image.naturalHeight);
    const canvas = document.createElement('canvas');
    canvas.width = sourceWidth;
    canvas.height = sourceHeight;
    canvas.getContext('2d').drawImage(image, sourceX, sourceY, sourceWidth, sourceHeight, 0, 0, sourceWidth, sourceHeight);
    canvas.toBlob((blob) => {
      if (!blob) {
        setMessage('Could not create the cropped bill image.');
        return;
      }
      const croppedFile = new File([blob], `cropped-bill-${Date.now()}.jpg`, { type: 'image/jpeg' });
      setCropConfirmation(croppedFile);
      setImageDebug((debug) => ({ ...debug, cropped: { width: sourceWidth, height: sourceHeight, bytes: croppedFile.size, rotation: manualRotation } }));
    }, 'image/jpeg', 0.98);
  };

  const confirmCrop = () => {
    const croppedFile = cropConfirmation;
    if (!croppedFile) return;
    setFile(croppedFile); setPreview(URL.createObjectURL(croppedFile));
    URL.revokeObjectURL(cropSource); setCropSource(null); setCropConfirmation(null);
    setMessage('Crop confirmed. The complete rotated image region will be sent to OpenCV and docTR.');
  };

  const handleScan = async (e) => {
    e.preventDefault();
    if (!file) {
      setMessage('Please capture or upload a bill image.');
      return;
    }
    setLoading(true);
    setProcessing(true);
    setMessage('');
    setDetectedBill(null);
    setExtractedData(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      if (originalUpload) formData.append('originalImage', originalUpload);
      if (cropFile) formData.append('rotatedImage', cropFile);
      formData.append('clientImageDiagnostics', JSON.stringify({ ...imageDebug, upload: { filename: file.name, bytes: file.size, rotation: manualRotation } }));
      if (agencyId) {
        formData.append('agencyId', agencyId);
        const agency = allAgencies.find(a => String(a.id) === String(agencyId));
        const linkedAgency = myAgencies.find(a => String(a.agencyId) === String(agencyId));
        formData.append('agencyName', agency?.name || linkedAgency?.agencyName || agencyName);
      }
      const res = await scanBill(shopId, formData);
      setDetectedBill(res.data);
      const result = res.data.extractedInvoice || res.data;
      const detectedSupplier = result.seller?.name || '';
      const matchedAgency = matchSupplierToAgency(detectedSupplier);
      if (matchedAgency) {
        setAgencyId(String(matchedAgency.id));
        setAgencyName(matchedAgency.name);
        setAgencyResolution({ matched: true, detectedSupplier, agency: matchedAgency });
      } else {
        setAgencyId('');
        setAgencyName('');
        setAgencyResolution({ matched: false, detectedSupplier, agency: null });
        setAgencyDraft(detectedSupplier);
      }
      setExtractedData(result.invoice ? {
        ...result,
        invoiceNo: result.invoice.invoice_number,
        invoiceDate: (() => {
          const rawDate = result.invoice.invoice_date || '';
          if (/^\d{4}-\d{2}-\d{2}$/.test(rawDate)) return rawDate;
          const match = rawDate.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/);
          return match ? `${match[3]}-${match[2].padStart(2, '0')}-${match[1].padStart(2, '0')}` : '';
        })(),
        // Retain OCR in detectedSupplierName, but use the registered agency's
        // canonical spelling whenever matching was confident.
        agencyName: matchedAgency?.name || result.seller?.name || '',
        gstNumber: result.seller?.gstin || '',
        totalAmount: result.total || 0,
        detectedSupplierName: detectedSupplier,
        itemsJson: JSON.stringify(mapOcrItems(result.items || [])),
        parsedItems: JSON.stringify(mapOcrItems(result.items || []))
      } : result);
      setMessage(matchedAgency
        ? `Bill scanned. Supplier matched to ${matchedAgency.name}. Review the invoice before saving.`
        : `Bill scanned. ${detectedSupplier || 'The detected supplier'} is not registered in your agency list; select an agency before saving.`);
      await loadData();
    } catch (err) {
      const backendMsg = err.response?.data;
      if (typeof backendMsg === 'string' && backendMsg.startsWith('Scan failed:')) {
        setMessage(backendMsg);
      } else if (backendMsg) {
        setMessage('Scan failed: ' + backendMsg);
      } else {
        setMessage('Scan failed. Please check backend console for details.');
      }
    } finally {
      setLoading(false);
      setProcessing(false);
    }
  };

  const normalizeSupplierName = (value) => (value || '')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\s+(DR|OS)$/g, '')
    .trim();

  const matchSupplierToAgency = (supplier) => {
    const normalizedSupplier = normalizeSupplierName(supplier);
    if (!normalizedSupplier) return null;
    const agencies = myAgencies.map(link => ({ id: link.agencyId, name: link.agencyName }));
    const exact = agencies.find(agency => normalizeSupplierName(agency.name) === normalizedSupplier);
    if (exact) return exact;
    // OCR occasionally appends a short garbage token (for example "DR").
    // Only accept a prefix match when the registered supplier has at least
    // three words; this deliberately avoids matching similar suppliers.
    return agencies.find(agency => {
      const normalizedAgency = normalizeSupplierName(agency.name);
      return normalizedAgency.split(' ').length >= 3
        && normalizedSupplier.startsWith(`${normalizedAgency} `);
    }) || null;
  };

  const mapOcrItems = (items) => items.map(item => ({
    ...item,
    productName: item.product || item.productName || item.name || ''
  }));

  const addDetectedAgency = async () => {
    const name = agencyDraft.trim();
    if (name.length < 3) {
      setMessage('Enter a valid supplier name before adding the agency.');
      return;
    }
    setAddingAgency(true);
    try {
      const response = await registerShopAgency(shopId, name);
      const linked = response.data;
      setMyAgencies(current => current.some(a => String(a.agencyId) === String(linked.agencyId))
        ? current : [...current, linked]);
      setAgencyId(String(linked.agencyId));
      setAgencyName(linked.agencyName);
      setAgencyResolution(current => ({
        matched: true,
        detectedSupplier: current?.detectedSupplier || extractedData?.detectedSupplierName || '',
        agency: { id: linked.agencyId, name: linked.agencyName }
      }));
      setMessage(`Agency ${linked.agencyName} is now linked to this shop and selected for this invoice.`);
      loadData();
    } catch (error) {
      setMessage(error.response?.data || 'Could not add this agency.');
    } finally {
      setAddingAgency(false);
    }
  };

  const handleVerify = async (e) => {
    e.preventDefault();
    if (!extractedData) return;
    if (!agencyId) {
      setMessage('Select a linked agency before confirming so the stock is saved to that agency inventory.');
      return;
    }
    try {
      const document = {
        ...extractedData,
        invoice: {
          ...(extractedData.invoice || {}),
          invoice_number: extractedData.invoiceNo,
          invoice_date: extractedData.invoiceDate?.split('-').reverse().join('-')
        },
        seller: { ...(extractedData.seller || {}), name: extractedData.agencyName, gstin: extractedData.gstNumber },
        detectedSupplierName: extractedData.detectedSupplierName || extractedData.agencyName,
        resolvedAgencyName: agencyResolution?.agency?.name || agencyName,
        items: parseItemsJson(extractedData.itemsJson || extractedData.parsedItems).map(item => ({
          ...item,
          product: item.product || item.productName || item.name
        }))
      };
      const selectedAgency = allAgencies.find(a => String(a.id) === String(agencyId));
      const linkedAgency = myAgencies.find(a => String(a.agencyId) === String(agencyId));
      await persistOcrInvoice(shopId, agencyId,
        selectedAgency?.name || linkedAgency?.agencyName || extractedData.agencyName, document);
      setMessage(`Invoice saved successfully. ${document.items.length} items were added to shop inventory. Agency: ${agencyResolution?.agency?.name || agencyName}.`);
      setDetectedBill(null);
      setExtractedData(null);
      setFile(null);
      setPreview(null);
      loadData();
    } catch (err) {
      setMessage(err.response?.data || 'Could not verify invoice.');
    }
  };

  const formatCurrency = (value) => {
    const num = Number(value);
    return isNaN(num) ? '0.00' : num.toFixed(2);
  };

  const parseItemsJson = (itemsJson) => {
    if (!itemsJson) return [];
    try {
      const parsed = typeof itemsJson === 'string' ? JSON.parse(itemsJson) : itemsJson;
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  };

  const renderBillItems = (itemsJson) => {
    const items = parseItemsJson(itemsJson);
    if (items.length === 0) return <span className="muted">No items</span>;
    return (
      <details className="scan-details">
        <summary>{items.length} items</summary>
        <div className="table-scroll">
          <table>
            <thead><tr><th>Product</th><th>Qty</th><th>Rate</th><th>Amount</th></tr></thead>
            <tbody>
              {items.map((item, idx) => (
                <tr key={idx}>
                  <td>{item.product || item.productName || item.name || '—'}</td>
                  <td>{item.quantity || item.qty || '—'}</td>
                  <td>₹{formatCurrency(item.rate || item.price || 0)}</td>
                  <td>₹{formatCurrency(item.amount || 0)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    );
  };

  const formatBillDate = (value) => {
    if (!value || value === 0 || String(value).startsWith('1970-01-01')) return 'Date unavailable';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'Date unavailable';
    return date.toLocaleDateString('en-GB');
  };

  const formatRawJson = (value) => {
    if (!value) return 'No raw JSON available';
    try { return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2); }
    catch { return String(value); }
  };

  return (
    <main className="portal-page">
      <header className="portal-heading">
        <div>
          <span>SMART INVENTORY</span>
          <h1>AI Bill Scanner</h1>
          <p>Capture or upload a bill. Our AI will detect the invoice, extract all details, and update your inventory automatically.</p>
        </div>
      </header>

      {message && <div className="portal-message">{message}</div>}

      {!detectedBill ? (
        <div className="tab-nav">
          <button className={activeTab === 'scan' ? 'active' : ''} onClick={() => setActiveTab('scan')}>Scan New Bill</button>
          <button className={activeTab === 'history' ? 'active' : ''} onClick={() => setActiveTab('history')}>Scan History ({scans.length})</button>
          <button className={activeTab === 'invoices' ? 'active' : ''} onClick={() => setActiveTab('invoices')}>Invoices ({invoices.length})</button>
          <button className={activeTab === 'agency' ? 'active' : ''} onClick={() => setActiveTab('agency')}>Agency Bills ({agencyBills.length})</button>
        </div>
      ) : (
        <div className="tab-nav">
          <button className="active" onClick={() => { setDetectedBill(null); setExtractedData(null); }}>← Back to scanning</button>
        </div>
      )}

      {!detectedBill && activeTab === 'scan' && (
        <section className="portal-card">
          <div className="card-title"><h2>Capture / Upload Bill</h2><span>Crop the invoice first, then AI corrects and extracts it</span></div>
          <form onSubmit={handleScan} className="scan-form">
            <div className="scan-upload-area">
              {!preview ? (
                <div className="scan-upload-options">
                  {!cameraActive ? (
                    <div className="scan-actions">
                      <button type="button" className="primary-action" onClick={startCamera}>📷 Capture Bill</button>
                      <label className="scan-label secondary">
                        <span>Or upload bill photo (JPG/PNG)</span>
                        <input type="file" accept="image/*" onChange={handleFileChange} />
                      </label>
                    </div>
                  ) : (
                    <div className="camera-container">
                      <video ref={videoRef} autoPlay playsInline className="camera-preview" />
                      <canvas ref={canvasRef} style={{ display: 'none' }} />
                      <div className="camera-actions">
                        <button type="button" className="primary-action" onClick={capturePhoto}>📸 Capture</button>
                        <button type="button" className="secondary-action" onClick={stopCamera}>Cancel</button>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div className="scan-preview-container">
                  <img src={preview} alt="Bill preview" className="scan-preview" />
                  <button type="button" className="scan-remove" onClick={() => { setPreview(null); setFile(null); }}>×</button>
                </div>
              )}
            </div>
            <div className="scan-form-row">
              <label className="scan-select-label">
                <span>Select Agency (optional)</span>
                <select value={agencyId} onChange={e => { setAgencyId(e.target.value); setAgencyName(''); }}>
                  <option value="">-- Auto-detect from my agencies --</option>
                  {myAgencies.map(a => <option key={a.id} value={a.agencyId}>{a.agencyName}</option>)}
                </select>
              </label>
              <button className="primary-action scan-action" disabled={loading || !file || processing}>
                {processing ? 'Processing...' : loading ? 'Scanning...' : 'Scan Bill with AI'}
              </button>
            </div>
            <div className="scan-info">
              <small>1. Select or capture the bill</small>
              <small>2. Crop only the invoice region</small>
              <small>3. OpenCV corrects the document perspective</small>
              <small>4. docTR extracts and validates invoice fields</small>
              <small>5. Review the result before inventory is updated</small>
            </div>
          </form>
        </section>
      )}

      {cropSource && (
        <div className="crop-modal" role="dialog" aria-modal="true" aria-labelledby="crop-title">
          <section className="crop-dialog">
            <div className="card-title"><h2 id="crop-title">Step 1: Rotate, then crop the complete bill</h2><span>Make sure the header, every medicine row, and totals are inside the crop.</span></div>
            <div className="crop-toolbar"><button type="button" className="secondary-action" onClick={rotateCropImage}>↻ Rotate 90°</button><button type="button" className="secondary-action" onClick={() => setCropZoom((zoom) => Math.max(.75, zoom - .25))}>− Zoom</button><button type="button" className="secondary-action" onClick={() => setCropZoom(1)}>Fit</button><button type="button" className="secondary-action" onClick={() => setCropZoom((zoom) => Math.min(2.5, zoom + .25))}>+ Zoom</button></div>
            <div className="crop-scroll"><div className="crop-frame" ref={cropFrameRef} style={{ width: `${cropZoom * 100}%` }} onPointerDown={beginCrop} onPointerMove={moveCrop} onPointerUp={finishCropSelection} onPointerCancel={finishCropSelection}>
              <img ref={cropImageRef} src={cropSource} alt="Select the bill region" draggable="false" />
              <div className="crop-selection" style={{ left: `${cropRect.x}%`, top: `${cropRect.y}%`, width: `${cropRect.width}%`, height: `${cropRect.height}%` }} />
            </div></div>
            <p className="crop-hint">The cropped image keeps its original resolution. OpenCV will still perform perspective correction after upload.</p>
            <div className="verification-actions">
              <button type="button" className="secondary-action" onClick={() => { URL.revokeObjectURL(cropSource); setCropSource(null); }}>Cancel</button>
              <button type="button" className="primary-action" onClick={createCroppedFile}>Crop &amp; Continue</button>
            </div>
          </section>
        </div>
      )}

      {cropConfirmation && (
        <div className="crop-modal" role="dialog" aria-modal="true" aria-labelledby="crop-confirm-title">
          <section className="crop-dialog crop-confirmation">
            <div className="card-title"><h2 id="crop-confirm-title">Step 3: Confirm crop</h2><span>Make sure the entire bill is inside the crop before scanning.</span></div>
            <img className="crop-confirm-preview" src={URL.createObjectURL(cropConfirmation)} alt="Cropped bill ready for scanning" />
            <div className="verification-actions"><button type="button" className="secondary-action" onClick={() => setCropConfirmation(null)}>Back</button><button type="button" className="primary-action" onClick={confirmCrop}>Use crop for Scan Bill</button></div>
          </section>
        </div>
      )}

      {detectedBill && extractedData && (
        <section className="portal-card wide verification-card">
          <div className="card-title"><h2>Verify Extracted Invoice</h2><span>Review and confirm the AI-extracted data</span></div>
          <form onSubmit={handleVerify} className="verification-form">
            <div className="verification-section">
              <h3>Invoice Details</h3>
              <div className="verification-grid">
                <div className="verification-field">
                  <label>Invoice Number</label>
                  <input type="text" value={extractedData.invoiceNo || ''} onChange={e => setExtractedData({...extractedData, invoiceNo: e.target.value})} placeholder="Invoice number" />
                </div>
                <div className="verification-field">
                  <label>Invoice Date</label>
                  <input type="date" value={extractedData.invoiceDate || ''} onChange={e => setExtractedData({...extractedData, invoiceDate: e.target.value})} />
                </div>
                <div className="verification-field">
                  <label>Supplier / Agency</label>
                  <input type="text" value={extractedData.agencyName || ''} onChange={e => setExtractedData({...extractedData, agencyName: e.target.value})} />
                </div>
                <div className="verification-field">
                  <label>GSTIN</label>
                  <input type="text" value={extractedData.gstNumber || ''} onChange={e => setExtractedData({...extractedData, gstNumber: e.target.value})} placeholder="Not detected" />
                </div>
                <div className="verification-field">
                  <label>Total Amount</label>
                  <input type="number" step="0.01" value={extractedData.totalAmount || 0} onChange={e => setExtractedData({...extractedData, totalAmount: e.target.value})} />
                </div>
              </div>
            </div>

            <div className={`agency-resolution ${agencyResolution?.matched ? 'matched' : 'unmatched'}`}>
              <h3>{agencyResolution?.matched ? '✓ Supplier matched to registered agency' : '⚠ Supplier not registered'}</h3>
              <p><b>Detected supplier:</b> {agencyResolution?.detectedSupplier || extractedData.detectedSupplierName || extractedData.agencyName || 'Not detected'}</p>
              {agencyResolution?.matched
                ? <p><b>Resolved agency:</b> {agencyResolution.agency.name}</p>
                : <>
                    <p>This supplier is not currently in your shop agency list. Add it, or select an existing agency.</p>
                    <label className="scan-select-label">
                      <span>Agency name</span>
                      <input value={agencyDraft} onChange={e => setAgencyDraft(e.target.value)} placeholder="Supplier / agency name" />
                    </label>
                    <button type="button" className="primary-action add-agency-action" disabled={addingAgency} onClick={addDetectedAgency}>
                      {addingAgency ? 'Adding agency…' : 'Add Agency & Continue'}
                    </button>
                    <p className="agency-or">OR</p>
                  </>}
              <label className="scan-select-label">
                <span>{agencyResolution?.matched ? 'Change agency (optional)' : 'Select existing agency'}</span>
                <select value={agencyId} onChange={e => {
                  const selected = myAgencies.find(a => String(a.agencyId) === e.target.value);
                  setAgencyId(e.target.value);
                  setAgencyName(selected?.agencyName || '');
                  setAgencyResolution(current => ({
                    matched: Boolean(selected),
                    detectedSupplier: current?.detectedSupplier || extractedData.detectedSupplierName || extractedData.agencyName,
                    agency: selected ? { id: selected.agencyId, name: selected.agencyName } : null
                  }));
                }}>
                  <option value="">-- Select an existing linked agency --</option>
                  {myAgencies.map(a => <option key={a.id} value={a.agencyId}>{a.agencyName}</option>)}
                </select>
              </label>
            </div>

            <div className="verification-section">
              <h3>Line Items</h3>
              <div className="table-scroll">
                <table>
                  <thead><tr><th>Product</th><th>Qty</th><th>Rate</th><th>Amount</th></tr></thead>
                  <tbody>
                    {parseItemsJson(extractedData.itemsJson || extractedData.parsedItems).map((item, idx) => (
                      <tr key={idx}>
                        <td><input type="text" value={item.product || item.productName || item.name || ''} onChange={e => { const items = [...parseItemsJson(extractedData.itemsJson || extractedData.parsedItems)]; items[idx] = {...items[idx], product: e.target.value, productName: e.target.value, name: e.target.value}; setExtractedData({...extractedData, itemsJson: JSON.stringify(items), parsedItems: JSON.stringify(items)}); }} /></td>
                        <td><input type="number" value={item.quantity || item.qty || 1} onChange={e => { const items = [...parseItemsJson(extractedData.itemsJson || extractedData.parsedItems)]; items[idx] = {...items[idx], quantity: Number(e.target.value)}; setExtractedData({...extractedData, itemsJson: JSON.stringify(items), parsedItems: JSON.stringify(items)}); }} /></td>
                        <td><input type="number" step="0.01" value={item.rate || item.price || 0} onChange={e => { const items = [...parseItemsJson(extractedData.itemsJson || extractedData.parsedItems)]; items[idx] = {...items[idx], rate: e.target.value, price: e.target.value}; setExtractedData({...extractedData, itemsJson: JSON.stringify(items), parsedItems: JSON.stringify(items)}); }} /></td>
                        <td><input type="number" step="0.01" value={item.amount || 0} onChange={e => { const items = [...parseItemsJson(extractedData.itemsJson || extractedData.parsedItems)]; items[idx] = {...items[idx], amount: e.target.value}; setExtractedData({...extractedData, itemsJson: JSON.stringify(items), parsedItems: JSON.stringify(items)}); }} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="verification-actions">
              <button type="button" className="secondary-action" onClick={() => { setDetectedBill(null); setExtractedData(null); }}>Discard</button>
              <button type="submit" className="primary-action">Confirm & Save Invoice</button>
            </div>
          </form>
        </section>
      )}

      {activeTab === 'history' && (
        <section className="portal-card wide">
          <div className="card-title"><h2>Scan History</h2><span>All previously scanned bills</span></div>
          {scans.length === 0 ? <p className="muted">No scans yet. Upload a bill to get started.</p> : (
            <div className="scan-grid">
              {scans.map(scan => (
                <div key={scan.id} className="scan-card">
                  <div className="scan-card-header">
                    <b>Scan #{scan.id}</b>
                    <span className={`status-badge ${scan.processingStatus.toLowerCase()}`}>{scan.processingStatus}</span>
                  </div>
                  <small>{scan.originalFileName}</small>
                  <small>{new Date(scan.createdAt).toLocaleString()}</small>
                  {scan.extractedText && <details className="scan-details"><summary>View extracted text</summary><pre className="ocr-text-viewer">{scan.extractedText}</pre></details>}
                  {scan.processingError && <div className="scan-error"><b>Scan failed</b><span>{scan.processingError}</span></div>}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {activeTab === 'invoices' && (
        <section className="portal-card wide">
          <div className="card-title"><h2>Invoices</h2><span>Parsed invoice data with supplier, customer and items</span></div>
          {invoices.length === 0 ? <p className="muted">No invoices yet.</p> : (
            <div className="invoice-list">
              {invoices.map(inv => (
                <div key={inv.id} className="invoice-card">
                  <div className="invoice-header">
                    <div>
                      <b>Invoice #{inv.invoiceNo}</b>
                      <small>{inv.invoiceType} • {inv.invoiceDate} • {inv.agencyName}</small>
                    </div>
                    <span className={`status-badge`}>{inv.totalItems} items • ₹{Number(inv.totalAmount).toFixed(2)}</span>
                  </div>
                  <div className="invoice-meta">
                    <div><b>Supplier</b><small>{inv.supplierJson || 'N/A'}</small></div>
                    <div><b>Customer</b><small>{inv.partyJson || 'N/A'}</small></div>
                  </div>
                  <details className="scan-details">
                    <summary>View {inv.totalItems} items</summary>
                    <pre className="invoice-json-viewer">{formatRawJson(inv.rawText)}</pre>
                  </details>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {activeTab === 'agency' && (
        <section className="portal-card wide">
          <div className="card-title"><h2>Agency Bills</h2><span>Bills linked to your agency supplies</span></div>
          {agencyBills.length === 0 ? <p className="muted">No agency bills yet.</p> : (
            <div className="table-scroll">
              <table>
                <thead><tr><th>Bill ID</th><th>Agency</th><th>Date</th><th>Amount</th><th>Status</th><th>Items</th></tr></thead>
                <tbody>
                  {agencyBills.map(bill => (
                    <tr key={bill.id}>
                      <td>#{bill.id}</td>
                      <td>{bill.agencyName}</td>
                      <td>{formatBillDate(bill.billDate)}</td>
                      <td>₹{Number(bill.totalAmount).toFixed(2)}</td>
                      <td><span className={`status-badge ${bill.paymentStatus.toLowerCase()}`}>{bill.paymentStatus}</span></td>
                      <td>{renderBillItems(bill.itemsJson)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </main>
  );
}
