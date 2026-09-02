import React from 'react';
import { BrowserRouter, Routes, Route, Link, Navigate, useLocation } from 'react-router-dom';
import MapPage from './pages/MapPage';
import ShopkeeperDashboard from './pages/ShopkeeperDashboard';
import LoginPage from './pages/LoginPage';
import AdminDashboard from './pages/AdminDashboard';
import './styles.css';

function Navbar() {
  const { pathname } = useLocation();
  const role = localStorage.getItem('role');
  const name = localStorage.getItem('name');

  if (pathname === '/') return null;

  const logout = () => {
    localStorage.clear();
    window.location.href = '/';
  };

  return (
    <nav className="site-nav">
      <Link to="/" className="site-brand"><span className="brand-mark">+</span> MediFind</Link>
      <div className="site-links">
        <Link to="/">Find Shops</Link>
        {role === 'SHOPKEEPER' && <Link to="/dashboard">Billing</Link>}
        {role === 'ADMIN' && <Link to="/admin">Admin</Link>}
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
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<ProtectedRoute allowedRole="SHOPKEEPER"><ShopkeeperDashboard /></ProtectedRoute>} />
        <Route path="/admin" element={<ProtectedRoute allowedRole="ADMIN"><AdminDashboard /></ProtectedRoute>} />
      </Routes>
    </BrowserRouter>
  );
}
