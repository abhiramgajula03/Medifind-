import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login, register } from '../services/api';

export default function LoginPage() {
  const navigate = useNavigate();
  const [isRegister, setIsRegister] = useState(false);
  const [role, setRole] = useState('CUSTOMER');
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '', shopName: '', shopAddress: '', pincode: '', licenseNumber: '', licensePhoto: '' });
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const set = (key, value) => setForm({ ...form, [key]: value });
  const readLicense = (file) => {
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) return setError('License image must be smaller than 10 MB');
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        const MAX_WIDTH = 1200;
        const MAX_HEIGHT = 1200;
        let width = img.width;
        let height = img.height;
        if (width > height) {
          if (width > MAX_WIDTH) {
            height *= MAX_WIDTH / width;
            width = MAX_WIDTH;
          }
        } else {
          if (height > MAX_HEIGHT) {
            width *= MAX_HEIGHT / height;
            height = MAX_HEIGHT;
          }
        }
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, width, height);
        const dataUrl = canvas.toDataURL('image/jpeg', 0.75);
        set('licensePhoto', dataUrl);
      };
      img.src = e.target.result;
    };
    reader.readAsDataURL(file);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    try {
      const res = isRegister ? await register({ ...form, role }) : await login(form);
      if (isRegister) {
        if (role === 'SHOPKEEPER') {
          setMessage(res.data.message || 'Application submitted for admin approval. You can login once approved.');
          setIsRegister(false);
          return;
        } else {
          setMessage('Account created successfully. You can now login.');
          setIsRegister(false);
          return;
        }
      }
      ['token', 'role', 'name', 'userId'].forEach((key) => localStorage.setItem(key, res.data[key]));
      if (res.data.shopId != null) localStorage.setItem('shopId', res.data.shopId);
      else localStorage.removeItem('shopId');
      navigate(res.data.role === 'ADMIN' ? '/admin' : res.data.role === 'SHOPKEEPER' ? '/dashboard' : '/');
    } catch (err) {
      const data = err.response?.data;
      const isNetworkError = !err.response;
      const msg = isNetworkError
        ? 'Cannot reach the MediFind backend at http://localhost:8081. Start the Spring Boot backend, then try again.'
        : typeof data === 'string'
        ? data
        : data?.error || data?.message || 'Something went wrong. Please check your credentials or try again.';
      setError(msg);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-logo">
          <span className="brand-mark">+</span>
          <div>
            <b>MediFind</b>
            <small>Secure access</small>
          </div>
        </div>
        <h1>{isRegister ? 'Create your account' : 'Welcome back'}</h1>
        <p>{isRegister ? 'Choose the account that fits you.' : 'Login to continue to MediFind.'}</p>
        {isRegister && (
          <div className="role-tabs">
            <button type="button" className={role === 'CUSTOMER' ? 'active' : ''} onClick={() => setRole('CUSTOMER')}>
              Customer
            </button>
            <button type="button" className={role === 'SHOPKEEPER' ? 'active' : ''} onClick={() => setRole('SHOPKEEPER')}>
              Shopkeeper
            </button>
          </div>
        )}
        {error && <div className="auth-error">{error}</div>}
        {message && <div className="auth-success">{message}</div>}
        <form onSubmit={handleSubmit} className="auth-form">
          {isRegister && <input placeholder="Full name" required value={form.name} onChange={(e) => set('name', e.target.value)} />}
          <input type="email" placeholder="Email address" required value={form.email} onChange={(e) => set('email', e.target.value)} />
          <input type="password" placeholder="Password" required value={form.password} onChange={(e) => set('password', e.target.value)} />
          {isRegister && <input placeholder="Phone number" required value={form.phone} onChange={(e) => set('phone', e.target.value)} />}
          {isRegister && role === 'SHOPKEEPER' && (
            <div className="shopkeeper-fields">
              <input placeholder="Medical shop name" required value={form.shopName} onChange={(e) => set('shopName', e.target.value)} />
              <input placeholder="Complete shop address" required value={form.shopAddress} onChange={(e) => set('shopAddress', e.target.value)} />
              <div className="split-fields">
                <input placeholder="Pincode" required value={form.pincode} onChange={(e) => set('pincode', e.target.value)} />
                <input placeholder="License number" required value={form.licenseNumber} onChange={(e) => set('licenseNumber', e.target.value)} />
              </div>
              <label className="license-upload">
                <span>{form.licensePhoto ? '✓ License image attached' : 'Upload medical license photo'}</span>
                <input type="file" accept="image/*" required={!form.licensePhoto} onChange={(e) => readLicense(e.target.files[0])} />
              </label>
            </div>
          )}
          <button className="auth-submit">
            {isRegister && role === 'SHOPKEEPER' ? 'Submit for admin approval' : isRegister ? 'Create account' : 'Login'}
          </button>
        </form>
        <button className="auth-toggle" onClick={() => { setIsRegister(!isRegister); setError(''); setMessage(''); }}>
          {isRegister ? 'Already registered? Login' : 'New to MediFind? Create account'}
        </button>
        {!isRegister && <small className="admin-note">Admin accounts are issued by MediFind.</small>}
      </div>
    </div>
  );
}
