import React, { useEffect, useState } from 'react';
import { addShop, deleteShop, getAllShops, getShopkeeperApplications, reviewShopkeeperApplication, getAgencies, createAgency, deleteAgency } from '../services/api';

const blankShop = { name: '', ownerName: '', phone: '', address: '', pincode: '', licenseNumber: '', lat: 17.385, lng: 78.486, is24hr: false };
const blankAgency = { name: '', email: '', phone: '', address: '', licenseNumber: '', licensePhoto: '' };
export default function AdminDashboard() {
  const [shops, setShops] = useState([]), [applications, setApplications] = useState([]), [form, setForm] = useState(blankShop), [message, setMessage] = useState(''), [licensePreview, setLicensePreview] = useState(null);
  const [agencies, setAgencies] = useState([]), [agencyForm, setAgencyForm] = useState(blankAgency), [agencyTab, setAgencyTab] = useState(false);
  const load = async () => { try { const [s, a, ag] = await Promise.all([getAllShops(), getShopkeeperApplications(), getAgencies()]); setShops(s.data); setApplications(a.data); setAgencies(ag.data); } catch { setMessage('Could not load admin data.'); } };
  useEffect(() => { load(); }, []);
  const review = async (id, status) => { await reviewShopkeeperApplication(id, status, form.lat, form.lng); setMessage(`Application ${status.toLowerCase()}.`); load(); };
  const create = async e => { e.preventDefault(); await addShop(form); setForm(blankShop); setMessage('Shop added to MediFind.'); load(); };
  const remove = async shop => { if (!window.confirm(`Delete ${shop.name} from the database?`)) return; await deleteShop(shop.id); setMessage('Shop deleted.'); load(); };
  const readLicense = (file) => {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setAgencyForm({ ...agencyForm, licensePhoto: reader.result });
    reader.readAsDataURL(file);
  };
  const addAgency = async e => {
    e.preventDefault();
    try {
      await createAgency(agencyForm);
      setAgencyForm(blankAgency);
      setMessage('Agency added successfully.');
      load();
    } catch (err) {
      setMessage(err.response?.data || 'Failed to add agency.');
    }
  };
  const removeAgency = async id => {
    if (!window.confirm('Delete this agency?')) return;
    try {
      await deleteAgency(id);
      setMessage('Agency deleted.');
      load();
    } catch (err) {
      setMessage(err.response?.data || 'Failed to delete agency.');
    }
  };
  return <main className="portal-page"><header className="portal-heading"><div><span>ADMIN CONTROL</span><h1>Shop safety & approvals</h1><p>Verify licenses and maintain the trusted medical-shop database.</p></div><div className="metric"><b>{shops.length}</b><small>Active shops</small></div><div className="metric warning"><b>{applications.filter(a => a.approvalStatus === 'PENDING').length}</b><small>Pending reviews</small></div></header>
    {message && <div className="portal-message">{message}</div>}
    <div className="tab-nav" style={{marginBottom:18}}>
      <button className={!agencyTab ? 'active' : ''} onClick={() => setAgencyTab(false)}>Shops & Approvals</button>
      <button className={agencyTab ? 'active' : ''} onClick={() => setAgencyTab(true)}>Agencies ({agencies.length})</button>
    </div>
    {!agencyTab ? <><section className="portal-grid"><div className="portal-card wide"><div className="card-title"><h2>Shopkeeper applications</h2><span>License verification required</span></div>
      {applications.length === 0 ? <p className="muted">No applications yet.</p> : applications.map(a => <article className="application-row" key={a.id}><div><b>{a.shopName}</b><small>{a.name} · {a.phone}</small><small>{a.shopAddress} — {a.pincode}</small><span className={`status ${a.approvalStatus.toLowerCase()}`}>{a.approvalStatus}</span></div><div className="application-actions">{a.licensePhoto && <button type="button" onClick={() => setLicensePreview(a)}>View license</button>}{a.approvalStatus === 'PENDING' && <><button onClick={() => review(a.id, 'APPROVED')}>Approve</button><button className="danger" onClick={() => review(a.id, 'REJECTED')}>Reject</button></>}</div></article>)}
    </div><form className="portal-card" onSubmit={create}><div className="card-title"><h2>Add a medical shop</h2></div>{Object.keys(blankShop).filter(k => k !== 'is24hr').map(k => <input key={k} type={['lat','lng'].includes(k) ? 'number' : 'text'} step="any" placeholder={k.replace(/([A-Z])/g,' $1')} required value={form[k]} onChange={e => setForm({...form,[k]: ['lat','lng'].includes(k) ? Number(e.target.value) : e.target.value})} />)}<label className="check"><input type="checkbox" checked={form.is24hr} onChange={e => setForm({...form,is24hr:e.target.checked})}/> Open 24 hours</label><button className="primary-action">Add shop</button></form></section>
    <section className="portal-card shop-table"><div className="card-title"><h2>All medical shops</h2><span>Admin can remove malicious listings</span></div><div className="table-scroll"><table><thead><tr><th>Shop</th><th>Owner</th><th>Location</th><th>Status</th><th></th></tr></thead><tbody>{shops.map(s => <tr key={s.id}><td><b>{s.name}</b><small>{s.phone}</small></td><td>{s.ownerName || '—'}</td><td>{s.address}<small>{s.pincode}</small></td><td><span className={`status ${s.isOpen ? 'approved':'pending'}`}>{s.isOpen ? 'OPEN':'CLOSED'}</span></td><td><button className="delete-link" onClick={() => remove(s)}>Delete</button></td></tr>)}</tbody></table></div></section>
    {licensePreview && <div className="license-modal" role="dialog" aria-modal="true" onClick={() => setLicensePreview(null)}><div className="license-dialog" onClick={e => e.stopPropagation()}><div className="license-dialog-header"><div><span>MEDICAL LICENSE</span><h2>{licensePreview.shopName}</h2><small>{licensePreview.licenseNumber || 'License number not provided'}</small></div><button onClick={() => setLicensePreview(null)} aria-label="Close">×</button></div><div className="license-image-wrap"><img src={licensePreview.licensePhoto} alt={`${licensePreview.shopName} medical license`} onError={e => { e.currentTarget.style.display = 'none'; e.currentTarget.nextElementSibling.style.display = 'grid'; }} /><div className="license-image-error"><b>Image could not be displayed</b><small>Please ask the shopkeeper to upload the license again as a JPG or PNG image.</small></div></div><div className="license-dialog-footer"><span>Submitted by {licensePreview.name}</span><button onClick={() => setLicensePreview(null)}>Close preview</button></div></div></div>}
  </> : <><section className="portal-grid"><div className="portal-card"><div className="card-title"><h2>Add Agency</h2><span>Create a new medicine distributor</span></div><form onSubmit={addAgency} className="agency-form">{Object.keys(blankAgency).filter(k => k !== 'licensePhoto').map(k => <input key={k} type="text" placeholder={k.replace(/([A-Z])/g,' $1')} required value={agencyForm[k]} onChange={e => setAgencyForm({...agencyForm,[k]: e.target.value})} />)}<label className="license-upload"><span>{agencyForm.licensePhoto ? '✓ License attached' : 'License photo (optional)'}</span><input type="file" accept="image/*" onChange={e => readLicense(e.target.files[0])} /></label><button className="primary-action">Add Agency</button></form></div><div className="portal-card wide"><div className="card-title"><h2>All Agencies</h2><span>Managed by admin</span></div>{agencies.length === 0 ? <p className="muted">No agencies yet.</p> : <div className="agency-list">{agencies.map(a => <div key={a.id} className="agency-row"><div><b>{a.name}</b><small>{a.email} · {a.phone} · {a.address}</small></div><button className="danger" onClick={() => removeAgency(a.id)}>Delete</button></div>)}</div>}</div></section></>}
  </main>;
}
