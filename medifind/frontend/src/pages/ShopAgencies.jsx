import React, { useEffect, useState } from 'react';
import { getAgencies, getShopAgencies, addShopAgency, removeShopAgency } from '../services/api';

export default function ShopAgencies() {
  const shopId = localStorage.getItem('shopId');
  const shopName = localStorage.getItem('name');
  const [allAgencies, setAllAgencies] = useState([]);
  const [myAgencies, setMyAgencies] = useState([]);
  const [selectedAgency, setSelectedAgency] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (shopId) {
      Promise.all([getAgencies(), getShopAgencies(shopId)])
        .then(([aRes, sRes]) => {
          setAllAgencies(aRes.data);
          setMyAgencies(sRes.data);
        })
        .catch(() => setMessage('Could not load agencies.'));
    }
  }, [shopId]);

  const handleAdd = async (e) => {
    e.preventDefault();
    if (!selectedAgency) return;
    const agency = allAgencies.find(a => a.id == selectedAgency);
    if (!agency) return;
    try {
      await addShopAgency(shopId, { agencyId: agency.id, agencyName: agency.name, shopName });
      setMessage(`Added ${agency.name} to your shop.`);
      setSelectedAgency('');
      const sRes = await getShopAgencies(shopId);
      setMyAgencies(sRes.data);
    } catch (err) {
      setMessage(err.response?.data || 'Failed to add agency.');
    }
  };

  const handleRemove = async (agencyId) => {
    try {
      await removeShopAgency(shopId, agencyId);
      setMessage('Agency removed.');
      const sRes = await getShopAgencies(shopId);
      setMyAgencies(sRes.data);
    } catch (err) {
      setMessage(err.response?.data || 'Failed to remove agency.');
    }
  };

  const availableAgencies = allAgencies.filter(a => !myAgencies.some(ma => ma.agencyId === a.id));

  return (
    <main className="portal-page">
      <header className="portal-heading">
        <div>
          <span>SHOPKEEPER DESK</span>
          <h1>My Agencies</h1>
          <p>Manage the agencies you collaborate with for medicine supply. These will be used during AI bill scanning.</p>
        </div>
      </header>

      {message && <div className="portal-message">{message}</div>}

      <section className="portal-grid">
        <div className="portal-card">
          <div className="card-title"><h2>Add New Agency</h2><span>Select an agency to collaborate with</span></div>
          {allAgencies.length === 0 ? (
            <p className="muted">No agencies available. Please ask the admin to add agencies first.</p>
          ) : availableAgencies.length === 0 ? (
            <p className="muted">You have linked all available agencies.</p>
          ) : (
            <form onSubmit={handleAdd} className="agency-form">
              <label className="scan-select-label">
                <span>Select Agency</span>
                <select value={selectedAgency} onChange={e => setSelectedAgency(e.target.value)} required>
                  <option value="">-- Choose agency --</option>
                  {availableAgencies.map(a => <option key={a.id} value={a.id}>{a.name} — {a.address}</option>)}
                </select>
              </label>
              <button className="primary-action" disabled={!selectedAgency}>Add Agency</button>
            </form>
          )}
        </div>

        <div className="portal-card wide">
          <div className="card-title"><h2>My Collaborated Agencies</h2><span>Agencies linked to your shop for bill scanning and supply</span></div>
          {myAgencies.length === 0 ? <p className="muted">No agencies added yet. Add one to get started.</p> : (
            <div className="agency-list">
              {myAgencies.map(sa => (
                <div key={sa.id} className="agency-row">
                  <div>
                    <b>{sa.agencyName}</b>
                    <small>Added on {new Date(sa.createdAt).toLocaleDateString()}</small>
                  </div>
                  <button className="danger" onClick={() => handleRemove(sa.agencyId)}>Remove</button>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
