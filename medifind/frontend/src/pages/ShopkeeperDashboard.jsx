import React, { useEffect, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { createCustomerReceipt, getDailyBills, toggleShopStatus, getInventoryByShop, getShop } from '../services/api';

const blank = { customerName: '', inventoryId: '', medicineName: '', quantity: 1, price: '' };

export default function ShopkeeperDashboard() {
  const shopId = localStorage.getItem('shopId');
  const [form, setForm] = useState(blank);
  const [report, setReport] = useState({ bills: [], billCount: 0, medicinesSold: 0, revenue: 0 });
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [isOpen, setIsOpen] = useState(false);
  const [message, setMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  // Autocomplete state
  const [inventory, setInventory] = useState([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const [selectedStockItem, setSelectedStockItem] = useState(null);
  const [cart, setCart] = useState([]);
  const autocompleteRef = useRef(null);

  const loadReport = async () => {
    if (!shopId) return setMessage('No approved shop is linked to this account.');
    try {
      setReport((await getDailyBills(shopId, date)).data);
    } catch {
      setMessage('Could not load daily billing report.');
    }
  };

  const loadInventoryList = async () => {
    if (!shopId) return;
    try {
      const res = await getInventoryByShop(shopId);
      setInventory(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      console.error('Failed to load inventory list:', err);
    }
  };

  useEffect(() => {
    loadReport();
    loadInventoryList();
  }, [date]);

  useEffect(() => {
    if (!shopId) return;
    getShop(shopId).then(({ data }) => setIsOpen(Boolean(data?.isOpen))).catch(() => {
      setMessage('Could not load the saved shop status.');
    });
  }, [shopId]);

  // Click outside listener for dropdown
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (autocompleteRef.current && !autocompleteRef.current.contains(event.target)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Filter inventory based on input
  const query = (form.medicineName || '').trim().toLowerCase();
  const matchingMedicines = inventory.filter((item) =>
    item.medicineName && item.medicineName.toLowerCase().includes(query)
  );

  // Sync selectedStockItem when medicineName changes manually or via selection
  useEffect(() => {
    if (!form.medicineName) {
      setSelectedStockItem(null);
      return;
    }
    const match = inventory.find(
      (item) => item.medicineName.toLowerCase() === form.medicineName.trim().toLowerCase()
    );
    setSelectedStockItem(match || null);
  }, [form.medicineName, inventory]);

  const handleSelectMedicine = (item) => {
    const packSize = Number(item.packSize || 1);
    const defaultUnitPrice = Number(item.price || 0) / packSize;
    setForm((prev) => ({
      ...prev,
      inventoryId: item.id,
      medicineName: item.medicineName,
      quantity: 1,
      price: defaultUnitPrice.toFixed(2),
    }));
    setSelectedStockItem(item);
    setShowDropdown(false);
    setErrorMessage('');
  };

  const handleMedicineKeyDown = (e) => {
    if (!showDropdown || matchingMedicines.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIndex((prev) => (prev + 1) % matchingMedicines.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIndex((prev) => (prev - 1 + matchingMedicines.length) % matchingMedicines.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (matchingMedicines[highlightedIndex]) {
        handleSelectMedicine(matchingMedicines[highlightedIndex]);
      }
    } else if (e.key === 'Escape') {
      setShowDropdown(false);
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    const requestedQty = Number(form.quantity || 0);
    if (!form.medicineName.trim()) {
      setErrorMessage('Please select a medicine from inventory.');
      return;
    }
    if (requestedQty <= 0) {
      setErrorMessage('Quantity must be at least 1.');
      return;
    }

    if (!selectedStockItem) {
      setErrorMessage('Select a medicine from the current inventory list.');
      return;
    }
    const avail = Number(selectedStockItem.quantityInBaseUnits ?? selectedStockItem.quantity ?? 0);
    const alreadyInCart = cart
      .filter((line) => String(line.inventoryId) === String(selectedStockItem.id))
      .reduce((total, line) => total + Number(line.quantity), 0);
    if (requestedQty + alreadyInCart > avail) {
      setErrorMessage(`Only ${avail} units of ${selectedStockItem.medicineName} are available in inventory.`);
      return;
    }

    setCart((existing) => [...existing, {
        inventoryId: form.inventoryId,
        medicineName: form.medicineName,
        quantity: Number(form.quantity),
        price: Number(form.price),
        baseUnit,
        total: previewTotal,
      }]);
    setForm((previous) => ({ ...blank, customerName: previous.customerName }));
    setSelectedStockItem(null);
    setMessage('Medicine added to this customer bill. Add another item or save the receipt.');
  };

  const saveReceipt = async () => {
    if (cart.length === 0) return;
    setErrorMessage('');
    try {
      const result = await createCustomerReceipt(shopId, {
        customerName: form.customerName,
        lines: cart.map(({ inventoryId, medicineName, quantity, price }) => ({ inventoryId, medicineName, quantity, price })),
      });
      setCart([]);
      setForm(blank);
      setMessage(`Receipt ${result.data.receiptNo} saved. Inventory was reduced for ${result.data.lines.length} item(s).`);
      loadReport();
      loadInventoryList();
    } catch (err) {
      setErrorMessage(err.response?.data || 'Could not save the customer bill.');
    }
  };

  const toggle = async () => {
    try {
      await toggleShopStatus(shopId, !isOpen);
      setIsOpen(!isOpen);
    } catch {
      setMessage('Could not update shop status.');
    }
  };

  const lineAmount = Number(form.price || 0) * Number(form.quantity || 1);
  const previewSgst = lineAmount * Number(selectedStockItem?.sgstRate || 0) / 100;
  const previewCgst = lineAmount * Number(selectedStockItem?.cgstRate || 0) / 100;
  const previewTotal = (lineAmount + previewSgst + previewCgst).toFixed(2);

  const stockWarning = selectedStockItem && Number(form.quantity) > Number(selectedStockItem.quantityInBaseUnits ?? selectedStockItem.quantity ?? 0);
  const availableStock = selectedStockItem ? Number(selectedStockItem.quantityInBaseUnits ?? selectedStockItem.quantity ?? 0) : 0;
  const packSize = Number(selectedStockItem?.packSize || 1);
  const baseUnit = selectedStockItem?.baseUnit || selectedStockItem?.unit || 'units';

  return (
    <main className="portal-page">
      <header className="portal-heading">
        <div>
          <span>SHOPKEEPER DESK</span>
          <h1>Daily billing</h1>
          <p>Record customer sales and monitor inventory in real-time.</p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link
            className="primary-action"
            to="/scanner"
            style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center' }}
          >
            AI Bill Scanner
          </Link>
          <button className={`shop-status-toggle ${isOpen ? 'open' : ''}`} onClick={toggle}>
            <i />
            {isOpen ? 'Shop open' : 'Shop closed'}
          </button>
        </div>
      </header>

      {message && <div className="portal-message">{message}</div>}

      <section className="summary-cards">
        <div>
          <small>Bills today</small>
          <b>{report.billCount}</b>
        </div>
        <div>
          <small>Medicines sold</small>
          <b>{report.medicinesSold}</b>
        </div>
        <div>
          <small>Today’s revenue</small>
          <b>₹{Number(report.revenue).toFixed(2)}</b>
        </div>
      </section>

      <section className="portal-grid billing-grid">
        <form className="portal-card" onSubmit={submit} style={{ position: 'relative' }}>
          <div className="card-title">
            <h2>Create customer bill</h2>
            <span>Date and time recorded automatically</span>
          </div>

          {errorMessage && <div className="portal-message auth-error">{errorMessage}</div>}

          <input
            placeholder="Customer name (optional)"
            value={form.customerName}
            onChange={(e) => setForm({ ...form, customerName: e.target.value })}
          />

          {/* Medicine Autocomplete Container */}
          <div className="autocomplete-container" ref={autocompleteRef} style={{ position: 'relative' }}>
            <input
              placeholder="Type or select medicine name..."
              required
              value={form.medicineName}
              onChange={(e) => {
                setForm({ ...form, medicineName: e.target.value });
                setShowDropdown(true);
                setHighlightedIndex(0);
              }}
              onFocus={() => setShowDropdown(true)}
              onKeyDown={handleMedicineKeyDown}
              autoComplete="off"
            />

            {showDropdown && query.length > 0 && (
              <ul className="autocomplete-dropdown">
                {matchingMedicines.length === 0 ? (
                  <li className="autocomplete-no-match">No matching medicines in shop inventory</li>
                ) : (
                  matchingMedicines.map((item, idx) => (
                    <li
                      key={item.id || idx}
                      className={`autocomplete-item ${idx === highlightedIndex ? 'highlighted' : ''}`}
                      onMouseDown={() => handleSelectMedicine(item)}
                      onMouseEnter={() => setHighlightedIndex(idx)}
                    >
                      <div className="autocomplete-item-main">
                        <b>{item.medicineName}</b>
                        <small>₹{(Number(item.price || 0) / Number(item.packSize || 1)).toFixed(2)} / {item.baseUnit || item.unit || 'unit'}</small>
                      </div>
                      <span className={`stock-badge ${(item.quantity || 0) === 0 ? 'out' : (item.quantity || 0) < 10 ? 'low' : 'ok'}`}>
                        {Number(item.quantityInBaseUnits ?? item.quantity ?? 0)} {item.baseUnit || item.unit || 'units'}
                      </span>
                    </li>
                  ))
                )}
              </ul>
            )}
          </div>

          {/* Stock Display Pill */}
          {selectedStockItem && (
            <div className="stock-info-row stock-details">
              <div>
                <span className="stock-label">Available Stock: </span>
                <strong className={selectedStockItem.quantity > 0 ? 'stock-val-ok' : 'stock-val-out'}>
                  {availableStock} {baseUnit}{availableStock === 1 ? '' : 's'}
                </strong>
              </div>
              <small>
                {packSize > 1 && <>Pack: {selectedStockItem.pack || `${packSize} ${baseUnit}s/strip`} · </>}
                {selectedStockItem.batchNo && <>Batch: {selectedStockItem.batchNo} · </>}
                {selectedStockItem.expiryDate && <>Expiry: {selectedStockItem.expiryDate} · </>}
                {selectedStockItem.sourceAgency && <>Source: {selectedStockItem.sourceAgency} · </>}
                Default price is derived from the last purchase rate and remains editable.
              </small>
            </div>
          )}

          {!selectedStockItem && form.medicineName.trim() && (
            <div className="stock-info-row warning">
              <small>⚠️ Medicine not found in current inventory list</small>
            </div>
          )}

          <div className="split-fields">
            <div>
              <input
                type="number"
                min="1"
                placeholder={`Quantity (${baseUnit})`}
                required
                value={form.quantity}
                onChange={(e) => setForm({ ...form, quantity: e.target.value })}
              />
            </div>
            <div>
              <input
                type="number"
                min="0"
                step="0.01"
                placeholder={`Selling price / ${baseUnit} ₹`}
                required
                value={form.price}
                onChange={(e) => setForm({ ...form, price: e.target.value })}
              />
            </div>
          </div>

          {stockWarning && (
            <div className="stock-error-text">
              Only {availableStock} {baseUnit}s available in inventory.
            </div>
          )}

          {selectedStockItem && (Number(selectedStockItem.sgstRate || 0) > 0 || Number(selectedStockItem.cgstRate || 0) > 0) && (
            <div className="stock-info-row tax-reference">
              Tax auto-calculated: SGST {Number(selectedStockItem.sgstRate || 0)}% (₹{previewSgst.toFixed(2)}) · CGST {Number(selectedStockItem.cgstRate || 0)}% (₹{previewCgst.toFixed(2)})
            </div>
          )}

          <div className="bill-total">
            <span>Bill total</span>
            <b>₹{previewTotal}</b>
          </div>

          <button className="primary-action" disabled={stockWarning}>
            Add to bill
          </button>

          {cart.length > 0 && (
            <section className="billing-cart" aria-label="Current customer bill">
              <div className="card-title"><h3>Current bill · {cart.length} item(s)</h3></div>
              {cart.map((line, index) => (
                <div className="cart-line" key={`${line.inventoryId}-${index}`}>
                  <span><b>{line.medicineName}</b><small>{line.quantity} {line.baseUnit} × ₹{line.price.toFixed(2)}</small></span>
                  <span>₹{line.total}</span>
                  <button type="button" aria-label={`Remove ${line.medicineName}`} onClick={() => setCart((items) => items.filter((_, itemIndex) => itemIndex !== index))}>×</button>
                </div>
              ))}
              <div className="bill-total"><span>Receipt total</span><b>₹{cart.reduce((sum, line) => sum + Number(line.total), 0).toFixed(2)}</b></div>
              <button type="button" className="primary-action" onClick={saveReceipt}>Save customer bill</button>
            </section>
          )}
        </form>

        <div className="portal-card wide">
          <div className="card-title">
            <div>
              <h2>Sales report</h2>
              <span>Medicine-wise daily transaction data</span>
            </div>
            <input
              className="date-picker"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Medicine</th>
                  <th>Qty</th>
                  <th>Taxes</th>
                  <th>Total</th>
                </tr>
              </thead>
              <tbody>
                {report.bills.map((b) => (
                  <tr key={b.id}>
                    <td>
                      {new Date(b.billedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </td>
                    <td>
                      <b>{b.medicineName}</b>
                      <small style={{ display: 'block', color: '#7f8681' }}>
                        {b.customerName || 'Walk-in customer'}
                      </small>
                    </td>
                    <td>{b.quantity}</td>
                    <td>₹{(Number(b.sgst) + Number(b.cgst) + Number(b.serviceTax)).toFixed(2)}</td>
                    <td>
                      <b>₹{Number(b.total).toFixed(2)}</b>
                    </td>
                  </tr>
                ))}
                {!report.bills.length && (
                  <tr>
                    <td colSpan="5" className="empty-cell">
                      No bills recorded for this date.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </main>
  );
}
