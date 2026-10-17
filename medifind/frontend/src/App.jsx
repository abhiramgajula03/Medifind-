import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Link, Navigate, useLocation } from 'react-router-dom';
import MapPage from './pages/MapPage';
import ShopkeeperDashboard from './pages/ShopkeeperDashboard';
import BillScanner from './pages/BillScanner';
import ShopAgencies from './pages/ShopAgencies';
import Inventory from './pages/Inventory';
import LoginPage from './pages/LoginPage';
import AdminDashboard from './pages/AdminDashboard';
import Notifications from './pages/Notifications';
import CustomerPharmacy from './pages/CustomerPharmacy';
import { getNotificationCount, toggleShopStatus } from './services/api';
import './styles.css';

function NotificationBell() {
  const [count, setCount] = useState(0);
  const userId = localStorage.getItem('userId');

  useEffect(() => {
    if (!userId) return;
    let active = true;
    const refresh = () => {
      getNotificationCount(userId)
        .then((res) => { if (active) setCount(res.data || 0); })
        .catch(() => {});
    };
    refresh();
    const id = window.setInterval(refresh, 30000);
    return () => { active = false; window.clearInterval(id); };
  }, [userId]);

  if (!userId) return null;

  return (
    <Link to="/notifications" className="notif-bell" aria-label="Notifications">
      <span>🔔</span>
      {count > 0 && <i className="notif-badge">{count > 99 ? '99+' : count}</i>}
    </Link>
  );
}

function Navbar() {
  const { pathname } = useLocation();
  const role = localStorage.getItem('role');
  const name = localStorage.getItem('name');

  if (pathname === '/') return null;

  const logout = async () => {
    const shopId = localStorage.getItem('shopId');
    if (role === 'SHOPKEEPER' && shopId) {
      try {
        await toggleShopStatus(shopId, false);
      } catch (_) {
        // A failed close request must not trap the user in a logged-in state.
      }
    }
    localStorage.clear();
    window.location.href = '/';
  };

  return (
    <nav className="site-nav">
      <Link to="/" className="site-brand"><span className="brand-mark">+</span> MediFind</Link>
      <div className="site-links">
        <Link to="/">Find Shops</Link>
        {role === 'SHOPKEEPER' && <><Link to="/dashboard">Billing</Link><Link to="/scanner">AI Scanner</Link><Link to="/agencies">My Agencies</Link><Link to="/inventory">Inventory</Link></>}
        {role === 'ADMIN' && <Link to="/admin">Admin</Link>}
        <NotificationBell />
        {name ? <><span>{name}</span><button onClick={logout}>Logout</button></> : <Link to="/login">Login</Link>}
      </div>
    </nav>
  );
}

function ProtectedRoute({ children, allowedRole }) {
  return localStorage.getItem('role') === allowedRole ? children : <Navigate to="/login" />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <Routes>
        <Route path="/" element={<MapPage />} />
        <Route path="/customer/pharmacy/:shopId" element={<CustomerPharmacy />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<ProtectedRoute allowedRole="SHOPKEEPER"><ShopkeeperDashboard /></ProtectedRoute>} />
        <Route path="/scanner" element={<ProtectedRoute allowedRole="SHOPKEEPER"><BillScanner /></ProtectedRoute>} />
        <Route path="/agencies" element={<ProtectedRoute allowedRole="SHOPKEEPER"><ShopAgencies /></ProtectedRoute>} />
        <Route path="/inventory" element={<ProtectedRoute allowedRole="SHOPKEEPER"><Inventory /></ProtectedRoute>} />
        <Route path="/admin" element={<ProtectedRoute allowedRole="ADMIN"><AdminDashboard /></ProtectedRoute>} />
        <Route path="/notifications" element={localStorage.getItem('role') ? <Notifications /> : <Navigate to="/login" />} />
      </Routes>
    </BrowserRouter>
  );
}
