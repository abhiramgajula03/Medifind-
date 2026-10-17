import React, { useState, useCallback, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { GoogleMap, useJsApiLoader, Marker, InfoWindow, Circle, OverlayView } from '@react-google-maps/api';
import { getNearbyShops } from '../services/api';

const GOOGLE_MAPS_KEY = process.env.REACT_APP_GOOGLE_MAPS_KEY;
const HYDERABAD = { lat: 17.385, lng: 78.486 };

const mapOptions = {
  disableDefaultUI: true,
  zoomControl: false,
  streetViewControl: false,
  clickableIcons: false,
  styles: [
    { elementType: 'geometry', stylers: [{ color: '#172331' }] },
    { elementType: 'labels.text.stroke', stylers: [{ color: '#172331' }] },
    { elementType: 'labels.text.fill', stylers: [{ color: '#8896a7' }] },
    { featureType: 'administrative.locality', elementType: 'labels.text.fill', stylers: [{ color: '#b5c0cc' }] },
    { featureType: 'poi', elementType: 'labels.text.fill', stylers: [{ color: '#738496' }] },
    { featureType: 'poi.park', elementType: 'geometry', stylers: [{ color: '#15382f' }] },
    { featureType: 'road', elementType: 'geometry', stylers: [{ color: '#344252' }] },
    { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#1c2734' }] },
    { featureType: 'road.highway', elementType: 'geometry', stylers: [{ color: '#42536a' }] },
    { featureType: 'transit', elementType: 'geometry', stylers: [{ color: '#263544' }] },
    { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#0c3046' }] },
    { featureType: 'water', elementType: 'labels.text.fill', stylers: [{ color: '#567b91' }] },
  ],
};

function Icon({ children }) { return <span className="icon" aria-hidden="true">{children}</span>; }

export default function MapPage() {
  const { isLoaded } = useJsApiLoader({ googleMapsApiKey: GOOGLE_MAPS_KEY });
  const [userLocation, setUserLocation] = useState(null);
  const [shops, setShops] = useState([]);
  const [selectedShop, setSelectedShop] = useState(null);
  const [filter, setFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [favoriteIds, setFavoriteIds] = useState([]);
  const mapRef = useRef(null);

  const onMapLoad = useCallback((map) => { mapRef.current = map; }, []);

  const findNearbyShops = () => {
    setLoading(true);
    setError('');
    if (!navigator.geolocation) {
      setError('Location is not supported by this browser.');
      setLoading(false);
      return;
    }
    navigator.geolocation.getCurrentPosition(async ({ coords }) => {
      const location = { lat: coords.latitude, lng: coords.longitude };
      setUserLocation(location);
      try { sessionStorage.setItem('medifindCustomerLocation', JSON.stringify(location)); } catch (_) {}
      try {
        const res = await getNearbyShops(coords.latitude, coords.longitude, filter);
        setShops(res.data || []);
        mapRef.current?.panTo(location);
        mapRef.current?.setZoom(14);
      } catch (_) {
        setError('Could not reach the pharmacy service. Please try again.');
      } finally { setLoading(false); }
    }, () => {
      setError('Location permission is required to find pharmacies near you.');
      setLoading(false);
    }, { enableHighAccuracy: true, timeout: 10000 });
  };

  useEffect(() => {
    const timer = window.setTimeout(findNearbyShops, 350);
    return () => window.clearTimeout(timer);
  }, []);

  const focusShop = (shop) => {
    if (shop.lat == null || shop.lng == null) return;
    setSelectedShop(shop);
    mapRef.current?.panTo({ lat: shop.lat, lng: shop.lng });
    mapRef.current?.setZoom(16);
  };

  const distanceTo = (shop) => {
    if (!userLocation || shop.lat == null || shop.lng == null) return shop.distance;
    const rad = value => value * Math.PI / 180;
    const dLat = rad(shop.lat - userLocation.lat), dLng = rad(shop.lng - userLocation.lng);
    const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(userLocation.lat)) * Math.cos(rad(shop.lat)) * Math.sin(dLng / 2) ** 2;
    return `${(6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFixed(1)} km`;
  };

  const visibleShops = shops.filter((shop) => {
    const matchesFilter = filter === 'all' || (filter === 'open' && shop.isOpen) || (filter === '24hr' && shop.is24hr);
    const matchesQuery = !query || `${shop.name} ${shop.address}`.toLowerCase().includes(query.toLowerCase());
    return matchesFilter && matchesQuery;
  });

  useEffect(() => {
    if (!query.trim() || !shops.length) return;
    const match = visibleShops.find(shop => shop.lat != null && shop.lng != null);
    if (!match) return;
    const timer = window.setTimeout(() => focusShop(match), 400);
    return () => window.clearTimeout(timer);
  }, [query]);

  const toggleFavorite = (id) => setFavoriteIds((ids) => ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id]);

  const navigateTo = (shop) => {
    if (!userLocation || shop.lat == null || shop.lng == null) return;
    const origin = `${userLocation.lat},${userLocation.lng}`;
    const destination = `${shop.lat},${shop.lng}`;
    window.open(`https://www.google.com/maps/dir/?api=1&origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&travelmode=driving`, '_blank', 'noopener,noreferrer');
  };

  if (!isLoaded) return <div className="map-loading"><div className="loader" /><span>Preparing your map...</span></div>;

  return (
    <main className="medifind-shell">
      <section className="map-stage">
        <GoogleMap mapContainerClassName="google-map" zoom={13} center={userLocation || HYDERABAD} options={mapOptions} onLoad={onMapLoad}>
          {userLocation && <Circle center={userLocation} radius={3000} options={{ fillColor: '#7ee2a8', fillOpacity: .06, strokeColor: '#7ee2a8', strokeOpacity: .3 }} />}
          <OverlayView
            position={userLocation || HYDERABAD}
            mapPaneName={OverlayView.OVERLAY_MOUSE_TARGET}
            getPixelPositionOffset={() => ({ x: -18, y: -18 })}
          >
            <div className="map-location-marker" title="Medical shops near you">
              <span>+</span>
              <strong>Medical shops near you</strong>
            </div>
          </OverlayView>
          {shops.map((shop) => <Marker key={shop.id} position={{ lat: shop.lat, lng: shop.lng }} onClick={() => setSelectedShop(shop)} />)}
          {selectedShop && <InfoWindow position={{ lat: selectedShop.lat, lng: selectedShop.lng }} onCloseClick={() => setSelectedShop(null)}><div className="map-info"><strong>{selectedShop.name}</strong><span>{selectedShop.isOpen ? 'Open now' : 'Closed'}</span><small>{selectedShop.address}</small><button onClick={() => navigateTo(selectedShop)}>Navigate</button><Link to={`/customer/pharmacy/${selectedShop.id}`}>Pharmacy details &amp; medicine assistant →</Link></div></InfoWindow>}
        </GoogleMap>

        <header className="map-header">
          <Link to="/" className="map-brand"><span className="brand-mark">+</span><span>MediFind<small>PHARMACY NEAR YOU</small></span></Link>
          <Link to="/login" className="profile-button" aria-label="Login"><Icon>♙</Icon><span>Login</span></Link>
        </header>

        <div className="location-pill"><span className="pulse-dot" /><div><small>YOUR LOCATION</small><strong>{userLocation ? 'Current location' : 'Hyderabad, Telangana'}</strong></div><span>⌄</span></div>
        <button className="locate-button" onClick={findNearbyShops} aria-label="Use my location">◎</button>
      </section>

      <section className="search-sheet">
        <div className="sheet-handle" />
        <div className="sheet-heading"><div><span className="eyebrow">MEDICINE, RIGHT ON TIME</span><h1>What are you looking for?</h1></div><span className="open-badge"><i /> Shops open</span></div>

        <label className="search-box">
          <span>⌕</span>
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search pharmacy or area" />
          {query && <button onClick={() => setQuery('')} aria-label="Clear search">×</button>}
        </label>

        <div className="filter-row">
          {[['all', 'All shops'], ['open', 'Open now'], ['24hr', '24 hours']].map(([value, label]) => <button key={value} className={filter === value ? 'active' : ''} onClick={() => setFilter(value)}>{value === 'all' ? '⌖' : value === 'open' ? '●' : '☾'} {label}</button>)}
          <button className="near-me" onClick={findNearbyShops} disabled={loading}>{loading ? 'Finding...' : 'Use my location'}</button>
        </div>

        {error && <div className="notice">{error}</div>}

        <div className="results-header"><h2>{userLocation ? `${visibleShops.length} shops within 3 km` : 'Nearby pharmacies'}</h2><button onClick={findNearbyShops}>View all <span>›</span></button></div>
        <div className="shop-results">
          {visibleShops.map((shop) => (
            <article className="shop-row" key={shop.id} onClick={() => focusShop(shop)}>
              <div className="history-icon">⌖</div>
              <div className="shop-copy"><div className="shop-title-line"><h3>{shop.name}</h3>{shop.is24hr && <span>24/7</span>}</div><p>{shop.address}</p><small><b className={shop.isOpen ? 'open' : 'closed'}>{shop.isOpen ? 'Open now' : 'Closed'}</b>{distanceTo(shop) && <> · {distanceTo(shop)} away</>}</small></div>
              <button className={`heart ${favoriteIds.includes(shop.id) ? 'saved' : ''}`} onClick={(e) => { e.stopPropagation(); toggleFavorite(shop.id); }} aria-label="Save pharmacy">{favoriteIds.includes(shop.id) ? '♥' : '♡'}</button>
              <Link className="pharmacy-details-link" to={`/customer/pharmacy/${shop.id}`} onClick={(e) => e.stopPropagation()}>Details</Link>
              <button className="navigate-shop" onClick={(e) => { e.stopPropagation(); navigateTo(shop); }}>Navigate</button>
            </article>
          ))}
          {!visibleShops.length && <div className="empty-state"><span>⌕</span><strong>No pharmacies match that search</strong><small>Try another name or filter</small></div>}
        </div>
      </section>

      <nav className="bottom-nav">
        <button className="active"><Icon>⌖</Icon><span>Find shops</span></button>
        <button><Icon>▤</Icon><span>Requests</span></button>
        <button><Icon>♡</Icon><span>Saved</span></button>
        <Link to="/login"><Icon>♙</Icon><span>Profile</span></Link>
      </nav>
    </main>
  );
}
