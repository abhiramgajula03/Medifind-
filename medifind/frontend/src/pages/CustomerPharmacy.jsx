import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { checkPharmacyMedicine, getCustomerPharmacy, identifyPharmacyMedicine, searchPharmacyMedicines } from '../services/api';

const friendlyError = 'We could not complete that request. Please try again in a moment.';

export default function CustomerPharmacy() {
  const { shopId } = useParams();
  const [shop, setShop] = useState(null);
  const [loadingShop, setLoadingShop] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [searching, setSearching] = useState(false);
  const [messages, setMessages] = useState([{ from: 'assistant', text: "Hi! I'm your pharmacy assistant. I can check whether a medicine is listed at this pharmacy. What are you looking for?" }]);
  const [checkingId, setCheckingId] = useState(null);
  const [photo, setPhoto] = useState(null);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [photoMatches, setPhotoMatches] = useState([]);
  const fileRef = useRef(null);
  const preview = useMemo(() => photo ? URL.createObjectURL(photo) : '', [photo]);

  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);
  useEffect(() => {
    let active = true;
    getCustomerPharmacy(shopId).then(({ data }) => { if (active) setShop(data); })
      .catch(() => { if (active) setError('This pharmacy could not be found.'); })
      .finally(() => { if (active) setLoadingShop(false); });
    return () => { active = false; };
  }, [shopId]);

  useEffect(() => {
    const term = query.trim();
    if (term.length < 2) { setSuggestions([]); setSearching(false); return undefined; }
    let active = true;
    const timer = window.setTimeout(() => {
      setSearching(true);
      searchPharmacyMedicines(shopId, term).then(({ data }) => { if (active) setSuggestions(Array.isArray(data) ? data : []); })
        .catch(() => { if (active) setSuggestions([]); })
        .finally(() => { if (active) setSearching(false); });
    }, 300);
    return () => { active = false; window.clearTimeout(timer); };
  }, [query, shopId]);

  const checkMedicine = async (medicine) => {
    setCheckingId(medicine.id);
    setSuggestions([]);
    setPhotoMatches([]);
    setQuery('');
    setMessages(old => [...old, { from: 'customer', text: `Check ${medicine.name}` }]);
    try {
      const { data } = await checkPharmacyMedicine(shopId, medicine.id);
      const item = data.medicine;
      const status = item.available ? `Yes, ${item.name} is listed as available. Recorded stock: ${item.stockLabel}.` : `${item.name} is listed in this pharmacy's inventory, but it is currently out of stock.`;
      setMessages(old => [...old, { from: 'assistant', text: `${status} Availability reflects the pharmacy's latest recorded inventory. Please confirm with the shopkeeper before travelling.`, medicine: item }]);
    } catch (_) {
      setMessages(old => [...old, { from: 'assistant', text: friendlyError }]);
    } finally { setCheckingId(null); }
  };

  const usePhoto = (file) => {
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type)) { setError('Please choose a JPEG or PNG image.'); return; }
    if (file.size > 5 * 1024 * 1024) { setError('Choose an image smaller than 5 MB.'); return; }
    setError(''); setPhoto(file); setPhotoMatches([]);
  };

  const identifyPhoto = async () => {
    if (!photo) return;
    setPhotoBusy(true); setError('');
    const form = new FormData(); form.append('image', photo);
    try {
      const { data } = await identifyPharmacyMedicine(shopId, form);
      const options = data.suggestions || [];
      setPhotoMatches(options);
      setMessages(old => [...old, { from: 'customer', text: 'I uploaded a medicine photo.' }, { from: 'assistant', text: data.message || 'Choose the product that matches your package.' }]);
    } catch (err) {
      const message = err.response?.data?.message;
      setError(typeof message === 'string' ? message : 'Photo identification is unavailable. You can enter the medicine name instead.');
    } finally { setPhotoBusy(false); }
  };

  const contact = () => {
    if (!shop?.phone) { setError('This pharmacy has not listed a contact number.'); return; }
    window.location.href = `tel:${shop.phone.replace(/[^+\d]/g, '')}`;
  };

  const reachShop = () => {
    if (shop?.lat == null || shop?.lng == null || !Number.isFinite(Number(shop.lat)) || !Number.isFinite(Number(shop.lng))) {
      setError('Directions are unavailable because this pharmacy has no saved map location.'); return;
    }
    const destination = `${shop.lat},${shop.lng}`;
    const openDirections = (origin) => {
      const params = new URLSearchParams({ api: '1', destination, travelmode: 'driving' });
      if (origin) params.set('origin', `${origin.latitude},${origin.longitude}`);
      window.open(`https://www.google.com/maps/dir/?${params.toString()}`, '_blank', 'noopener,noreferrer');
    };
    if (!navigator.geolocation) { setError('Your browser cannot share your location. Opening directions to the pharmacy without a starting point.'); openDirections(null); return; }
    navigator.geolocation.getCurrentPosition(({ coords }) => openDirections(coords), () => {
      setError('Location was not available. Set your starting point in Google Maps to get directions.'); openDirections(null);
    }, { enableHighAccuracy: true, timeout: 10000 });
  };

  if (loadingShop) return <main className="customer-page"><div className="loader" /><p>Loading pharmacy...</p></main>;
  if (!shop) return <main className="customer-page"><Link to="/" className="back-link">← Find pharmacies</Link><div className="assistant-error">{error || 'This pharmacy could not be found.'}</div></main>;

  const activeMatches = photoMatches.length ? photoMatches : suggestions;
  let distanceLabel = '';
  try {
    const origin = JSON.parse(sessionStorage.getItem('medifindCustomerLocation') || 'null');
    if (origin && Number.isFinite(origin.lat) && Number.isFinite(origin.lng) && shop.lat != null && shop.lng != null) {
      const rad = value => value * Math.PI / 180;
      const dLat = rad(Number(shop.lat) - origin.lat), dLng = rad(Number(shop.lng) - origin.lng);
      const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(origin.lat)) * Math.cos(rad(Number(shop.lat))) * Math.sin(dLng / 2) ** 2;
      distanceLabel = `${(6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFixed(1)} km away`;
    }
  } catch (_) {}
  return (
    <main className="customer-page">
      <div className="customer-topline"><Link to="/" className="back-link">← Find pharmacies</Link><span className="assistant-tag">MEDIFIND PHARMACY ASSISTANT</span></div>
      <section className="pharmacy-summary">
        <div className="pharmacy-avatar">＋</div><div className="pharmacy-meta"><span className={`shop-status ${shop.isOpen ? 'open' : 'closed'}`}><i />{shop.isOpen ? 'Open now' : 'Closed'}{shop.is24hr ? ' · 24 hours' : ''}</span>
          <h1>{shop.name}</h1><p>{shop.address || 'Address not available'}{distanceLabel && <> · {distanceLabel}</>}</p>{shop.phone && <a href={`tel:${shop.phone.replace(/[^+\d]/g, '')}`} className="phone-link">{shop.phone}</a>}</div>
        <div className="pharmacy-actions"><button className="secondary-action" onClick={contact}>☎ Contact shopkeeper</button><button className="primary-action" onClick={reachShop}>↗ Reach shop</button></div>
      </section>

      <section className="assistant-layout">
        <div className="assistant-intro"><div className="assistant-orb">✦</div><div><span>HERE TO HELP</span><h2>AI Pharmacy Assistant</h2><p>Ask about medicine availability at <b>{shop.name}</b>.</p></div></div>
        <div className="chat-panel" aria-live="polite">
          <div className="chat-log">{messages.map((message, index) => <div key={index} className={`chat-message ${message.from}`}><span className="message-avatar">{message.from === 'assistant' ? '✦' : 'You'}</span><div className="message-bubble">{message.text}{message.medicine && <small className="stock-note">{message.medicine.available ? `Recorded stock · ${message.medicine.stockLabel}` : 'Currently recorded as out of stock'}</small>}</div></div>)}
            {checkingId && <div className="chat-message assistant"><span className="message-avatar">✦</span><div className="message-bubble"><span className="inline-loader" /> Checking this pharmacy's inventory…</div></div>}
          </div>
          <div className="chat-composer"><div className="composer-input"><span>⌕</span><input value={query} onChange={e => setQuery(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && suggestions.length === 1) checkMedicine(suggestions[0]); }} placeholder="Type a medicine name..." aria-label="Medicine name" /><button aria-label="Upload medicine photo" title="Upload medicine photo" onClick={() => fileRef.current?.click()}>▧</button><input ref={fileRef} type="file" accept="image/jpeg,image/png" hidden onChange={e => usePhoto(e.target.files?.[0])} /></div>
            {query.trim().length >= 2 && <div className="medicine-suggestions">{searching ? <span>Searching this pharmacy…</span> : suggestions.length ? suggestions.map(item => <button key={item.id} onClick={() => checkMedicine(item)} disabled={Boolean(checkingId)}><span><b>{item.name}</b><small>{item.available ? `Recorded stock: ${item.stockLabel}` : 'Out of stock'}</small></span><i>›</i></button>) : <span>No matching product in this pharmacy's inventory.</span>}</div>}
            {photo && <div className="photo-draft"><img src={preview} alt="Medicine package preview"/><div><b>{photo.name}</b><small>Ready to identify · maximum 5 MB</small></div><button onClick={() => { setPhoto(null); setPhotoMatches([]); if (fileRef.current) fileRef.current.value = ''; }}>Remove</button><button className="identify-button" onClick={identifyPhoto} disabled={photoBusy}>{photoBusy ? 'Reading…' : 'Identify photo'}</button></div>}
            {activeMatches.length > 0 && <div className="photo-candidates"><b>Possible products · choose the exact one</b>{activeMatches.map(item => <button key={item.id} onClick={() => checkMedicine(item)} disabled={Boolean(checkingId)}>{item.name}<span>{item.available ? item.stockLabel : 'Out of stock'}</span></button>)}</div>}
            {error && <div className="assistant-error" role="alert">{error}</div>}
            <p className="privacy-note">Availability comes from this pharmacy’s recorded stock and should be confirmed with the shopkeeper.</p>
          </div>
        </div>
      </section>
    </main>
  );
}
