import React, { useEffect, useState } from 'react';
import {
  getInventoryByShop,
  searchInventory,
  getInventoryByAgencies,
  getInventorySummary,
  getInventorySources,
  clearShopInventory
} from '../services/api';

export default function Inventory() {
  const shopId = localStorage.getItem('shopId');
  const [view, setView] = useState('overall');
  const [inventory, setInventory] = useState([]);
  const [agencyInventory, setAgencyInventory] = useState({});
  const [sourcesMap, setSourcesMap] = useState({});
  const [summary, setSummary] = useState({});
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [showClearModal, setShowClearModal] = useState(false);
  const [clearing, setClearing] = useState(false);

  useEffect(() => {
    if (shopId) {
      setLoading(true);
      Promise.all([loadInventory(), loadSummary()])
        .catch((err) => {
          console.error('Inventory load error:', err);
          setMessage('Could not load inventory data.');
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
      setMessage('No shop linked to this account.');
    }
  }, [shopId, view]);

  const loadInventory = async () => {
    if (!shopId) return;
    try {
      if (view === 'overall') {
        const [resInv, resSources] = await Promise.all([
          getInventoryByShop(shopId),
          getInventorySources(shopId).catch(() => ({ data: {} }))
        ]);
        setInventory(Array.isArray(resInv.data) ? resInv.data : []);
        setSourcesMap(resSources.data || {});
      } else if (view === 'agencies') {
        const res = await getInventoryByAgencies(shopId);
        setAgencyInventory(res.data || {});
      }
    } catch (err) {
      console.error('loadInventory failed:', err);
      setMessage('Could not load inventory: ' + (err.response?.data || err.message));
    }
  };

  const loadSummary = async () => {
    if (!shopId) return;
    try {
      const res = await getInventorySummary(shopId);
      setSummary(res.data || {});
    } catch (err) {
      console.error('loadSummary failed:', err);
    }
  };

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!searchQuery.trim() || !shopId) return;
    try {
      const res = await searchInventory(shopId, searchQuery.trim());
      setSearchResults(Array.isArray(res.data) ? res.data : []);
      setView('search');
    } catch {
      setMessage('Search failed.');
    }
  };

  const handleClearInventory = async () => {
    if (!shopId) return;
    try {
      setClearing(true);
      await clearShopInventory(shopId);
      setShowClearModal(false);
      setMessage('Current shop inventory cleared.');
      setInventory([]);
      setSummary({ totalItems: 0, totalQuantity: 0, totalValue: 0, lowStock: 0, outOfStock: 0 });
      setSourcesMap({});
    } catch (err) {
      console.error('Clear inventory error:', err);
      setMessage('Could not clear inventory: ' + (err.response?.data?.message || err.message));
    } finally {
      setClearing(false);
    }
  };

  const formatCurrency = (value) => {
    const num = Number(value);
    return isNaN(num) ? '0.00' : num.toFixed(2);
  };

  const baseQuantity = (item) => Number(item.quantityInBaseUnits ?? item.quantity ?? 0);
  const baseUnit = (item) => item.baseUnit || item.unit || 'unit';
  const stockLabel = (item) => {
    const quantity = baseQuantity(item);
    const size = Number(item.packSize || 1);
    if (size <= 1) return `${quantity} ${baseUnit(item)}${quantity === 1 ? '' : 's'}`;
    const strips = Math.floor(quantity / size);
    const remainder = quantity % size;
    return remainder === 0
      ? `${quantity} ${baseUnit(item)}s (${strips} strips)`
      : `${quantity} ${baseUnit(item)}s (${strips} strips + ${remainder})`;
  };
  const unitPrice = (item) => Number(item.price || 0) / Number(item.packSize || 1);

  const getSourceDisplay = (medicineName) => {
    if (!medicineName) return 'Standard Stock';
    const key = medicineName.trim().toLowerCase();
    const sources = sourcesMap[key] || [];
    if (sources.length === 0) return 'Standard Stock';
    if (sources.length === 1) return sources[0];
    return `Multiple sources (${sources.length})`;
  };

  const getSourceTooltip = (medicineName) => {
    if (!medicineName) return '';
    const key = medicineName.trim().toLowerCase();
    const sources = sourcesMap[key] || [];
    return sources.length > 0 ? `Suppliers: ${sources.join(', ')}` : '';
  };

  if (loading) {
    return (
      <main className="portal-page">
        <header className="portal-heading">
          <div>
            <span>SHOPKEEPER DESK</span>
            <h1>Inventory Management</h1>
            <p>Track medicines across all agencies and overall physical stock levels.</p>
          </div>
        </header>
        <div className="portal-message">Loading inventory...</div>
      </main>
    );
  }

  return (
    <main className="portal-page">
      <header className="portal-heading">
        <div>
          <span>SHOPKEEPER DESK</span>
          <h1>Inventory Management</h1>
          <p>Track medicines across all agencies and overall physical stock levels.</p>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button
            className="danger-action-btn"
            onClick={() => setShowClearModal(true)}
            title="Reset current stock quantities for this shop"
          >
            🗑️ Clear Inventory
          </button>
        </div>
      </header>

      {message && <div className="portal-message">{message}</div>}

      <section className="summary-cards">
        <div className="summary-card">
          <small>Total Medicines</small>
          <b>{summary.totalItems || 0}</b>
        </div>
        <div className="summary-card">
          <small>Total Stock</small>
          <b>{summary.totalQuantity || 0}</b>
        </div>
        <div className="summary-card">
          <small>Stock Value</small>
          <b>₹{formatCurrency(summary.totalValue)}</b>
        </div>
        <div className="summary-card warning">
          <small>Low Stock</small>
          <b>{summary.lowStock || 0}</b>
        </div>
        <div className="summary-card danger">
          <small>Out of Stock</small>
          <b>{summary.outOfStock || 0}</b>
        </div>
      </section>

      <form onSubmit={handleSearch} className="search-box" style={{ marginBottom: 18 }}>
        <span>🔍</span>
        <input
          placeholder="Search medicine by name..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
        <button type="submit">Search</button>
      </form>

      <div className="tab-nav" style={{ marginBottom: 18 }}>
        <button className={view === 'overall' ? 'active' : ''} onClick={() => setView('overall')}>
          Shop Physical Inventory
        </button>
        <button className={view === 'agencies' ? 'active' : ''} onClick={() => setView('agencies')}>
          By Agency Source
        </button>
        {searchResults.length > 0 && (
          <button className={view === 'search' ? 'active' : ''} onClick={() => setView('search')}>
            Search Results ({searchResults.length})
          </button>
        )}
      </div>

      {view === 'overall' && (
        <section className="portal-card wide">
          <div className="card-title">
            <h2>Shop Inventory</h2>
            <span>Complete physical stock available in the shop</span>
          </div>
          {inventory.length === 0 ? (
            <div className="empty-inventory-state">
              <span style={{ fontSize: 32 }}>📦</span>
              <h3>No medicines in stock yet</h3>
              <p>Scan an agency bill using AI Scanner to add inventory.</p>
            </div>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Medicine</th>
                    <th>Available Stock</th>
                    <th>Unit</th>
                    <th>Price</th>
                    <th>Stock Value</th>
                    <th>Agency / Source</th>
                    <th>Last Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {inventory.map((item) => {
                    const sourceText = getSourceDisplay(item.medicineName);
                    const sourceTooltip = getSourceTooltip(item.medicineName);
                    return (
                      <tr key={item.id}>
                        <td>
                          <b>{item.medicineName}</b>
                        </td>
                        <td>
                          <span
                            className={`stock-pill ${
                              baseQuantity(item) === 0
                                ? 'out-of-stock'
                                : baseQuantity(item) < 10
                                ? 'low-stock'
                                : 'in-stock'
                            }`}
                          >
                            {stockLabel(item)}
                          </span>
                        </td>
                        <td>{item.pack || item.unit}</td>
                        <td>₹{formatCurrency(unitPrice(item))}/{baseUnit(item)}</td>
                        <td>
                          ₹{formatCurrency(unitPrice(item) * baseQuantity(item))}
                        </td>
                        <td>
                          <span
                            className="source-tag"
                            title={sourceTooltip}
                          >
                            {sourceText}
                          </span>
                        </td>
                        <td>
                          {item.updatedAt
                            ? new Date(item.updatedAt).toLocaleDateString()
                            : 'N/A'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {view === 'agencies' && (
        <section className="portal-card wide">
          <div className="card-title">
            <h2>Inventory by Agency Source</h2>
            <span>Medicines breakdown by supplying agency</span>
          </div>
          {Object.keys(agencyInventory).length === 0 ? (
            <p className="muted">No agency inventory data yet.</p>
          ) : (
            <div className="agency-inventory-grid">
              {Object.entries(agencyInventory).map(([agencyName, items]) => (
                <div key={agencyName} className="agency-inventory-card">
                  <h3>{agencyName}</h3>
                  <small>{items.length} medicines supplied</small>
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Medicine</th>
                          <th>Qty</th>
                          <th>Price</th>
                        </tr>
                      </thead>
                      <tbody>
                        {items.map((item) => (
                          <tr key={item.id}>
                            <td>{item.medicineName}</td>
                            <td>{stockLabel(item)}</td>
                            <td>₹{formatCurrency(unitPrice(item))}/{baseUnit(item)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {view === 'search' && (
        <section className="portal-card wide">
          <div className="card-title">
            <h2>Search Results</h2>
            <span>Found {searchResults.length} medicines</span>
          </div>
          {searchResults.length === 0 ? (
            <p className="muted">No medicines found matching "{searchQuery}".</p>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Medicine</th>
                    <th>Available Stock</th>
                    <th>Unit</th>
                    <th>Price</th>
                  </tr>
                </thead>
                <tbody>
                  {searchResults.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <b>{item.medicineName}</b>
                      </td>
                      <td>{stockLabel(item)}</td>
                      <td>{item.pack || item.unit}</td>
                      <td>₹{formatCurrency(unitPrice(item))}/{baseUnit(item)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* Confirmation Modal */}
      {showClearModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3>Clear current shop inventory?</h3>
            <p>
              This will remove all current stock quantities from this shop's inventory.
              <br /><br />
              Your invoices, agencies, agency bills, scan history and previous customer billing records will <strong>NOT</strong> be deleted.
            </p>
            <div className="modal-actions">
              <button
                className="secondary-action"
                onClick={() => setShowClearModal(false)}
                disabled={clearing}
              >
                Cancel
              </button>
              <button
                className="danger-action-btn"
                onClick={handleClearInventory}
                disabled={clearing}
              >
                {clearing ? 'Clearing...' : 'Clear Inventory'}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
