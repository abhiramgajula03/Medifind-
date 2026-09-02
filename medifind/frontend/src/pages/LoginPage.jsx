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
    if (file.size > 3 * 1024 * 1024) return setError('License image must be smaller than 3 MB');
    const reader = new FileReader();
    reader.onload = () => set('licensePhoto', reader.result);
    reader.readAsDataURL(file);
  };

  const handleSubmit = async (e) => {
    e.preventDefault(); setError(''); setMessage('');
    try {
      const res = isRegister ? await register({ ...form, role }) : await login(form);
      if (isRegister && role === 'SHOPKEEPER') {
        setMessage(res.data.message); setIsRegister(false); return;
      }
      ['token', 'role', 'name', 'userId', 'shopId'].forEach((key) => res.data[key] != null && localStorage.setItem(key, res.data[key]));
      navigate(res.data.role === 'ADMIN' ? '/admin' : res.data.role === 'SHOPKEEPER' ? '/dashboard' : '/');
    } catch (err) { setError(err.response?.data || 'Something went wrong'); }
  };

  return <div className="auth-page"><div className="auth-card">
    <div className="auth-logo"><span className="brand-mark">+</span><div><b>MediFind</b><small>Secure access</small></div></div>
    <h1>{isRegister ? 'Create your account' : 'Welcome back'}</h1>
    <p>{isRegister ? 'Choose the account that fits you.' : 'Login to continue to MediFind.'}</p>
    {isRegister && <div className="role-tabs"><button type="button" className={role === 'CUSTOMER' ? 'active' : ''} onClick={() => setRole('CUSTOMER')}>Customer</button><button type="button" className={role === 'SHOPKEEPER' ? 'active' : ''} onClick={() => setRole('SHOPKEEPER')}>Shopkeeper</button></div>}
    {error && <div className="auth-error">{String(error)}</div>}{message && <div className="auth-success">{message}. You can login after approval.</div>}
    <form onSubmit={handleSubmit} className="auth-form">
      {isRegister && <input placeholder="Full name" required value={form.name} onChange={e => set('name', e.target.value)} />}
      <input type="email" placeholder="Email address" required value={form.email} onChange={e => set('email', e.target.value)} />
      <input type="password" placeholder="Password" required value={form.password} onChange={e => set('password', e.target.value)} />
      {isRegister && <input placeholder="Phone number" required value={form.phone} onChange={e => set('phone', e.target.value)} />}
      {isRegister && role === 'SHOPKEEPER' && <div className="shopkeeper-fields">
        <input placeholder="Medical shop name" required value={form.shopName} onChange={e => set('shopName', e.target.value)} />
        <input placeholder="Complete shop address" required value={form.shopAddress} onChange={e => set('shopAddress', e.target.value)} />
        <div className="split-fields"><input placeholder="Pincode" required value={form.pincode} onChange={e => set('pincode', e.target.value)} /><input placeholder="License number" required value={form.licenseNumber} onChange={e => set('licenseNumber', e.target.value)} /></div>
        <label className="license-upload"><span>{form.licensePhoto ? '✓ License image attached' : 'Upload medical license photo'}</span><input type="file" accept="image/*" required={!form.licensePhoto} onChange={e => readLicense(e.target.files[0])} /></label>
      </div>}
      <button className="auth-submit">{isRegister && role === 'SHOPKEEPER' ? 'Submit for admin approval' : isRegister ? 'Create account' : 'Login'}</button>
    </form>
    <button className="auth-toggle" onClick={() => { setIsRegister(!isRegister); setError(''); }}>{isRegister ? 'Already registered? Login' : 'New to MediFind? Create account'}</button>
    {!isRegister && <small className="admin-note">Admin accounts are issued by MediFind.</small>}
  </div></div>;
}
