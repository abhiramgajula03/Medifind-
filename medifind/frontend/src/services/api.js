// src/services/api.js
// Central file for all API calls to Spring Boot backend

import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api';

// Attach JWT token to every request automatically
const api = axios.create({ baseURL: BASE_URL });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ─── AUTH ────────────────────────────────────────────────
export const register = (data) => api.post('/auth/register', data);
export const login = (data) => api.post('/auth/login', data);

// ─── SHOPS ───────────────────────────────────────────────
// Get shops near user's location
// filter: 'all' | 'open' | '24hr'
export const getNearbyShops = (lat, lng, filter = 'all') =>
  api.get(`/shops/nearby?lat=${lat}&lng=${lng}&filter=${filter}`);

// Get single shop detail
export const getShop = (id) => api.get(`/shops/${id}`);

// Admin: add shop
export const addShop = (shopData) => api.post('/shops', shopData);

// Admin: update shop
export const updateShop = (id, shopData) => api.put(`/shops/${id}`, shopData);

// Admin: delete shop
export const deleteShop = (id) => api.delete(`/shops/${id}`);

// Admin: all shops list
export const getAllShops = () => api.get('/shops/all');
export const getShopkeeperApplications = () => api.get('/admin/shopkeeper-applications');
export const reviewShopkeeperApplication = (id, status, lat, lng) => api.patch(`/admin/shopkeeper-applications/${id}`, { status, lat, lng });

// Shopkeeper: toggle open/close
export const toggleShopStatus = (shopId, open) =>
  api.patch(`/shops/${shopId}/status?open=${open}`);

// ─── SHORTAGE LIST ────────────────────────────────────────
// Shopkeeper adds walk-in/offline customer to shortage list
export const addOfflineShortage = (shopId, data) =>
  api.post(`/shortage/shop/${shopId}/offline`, data);

// Customer requests medicine online
export const requestMedicineOnline = (shopId, data) =>
  api.post(`/shortage/shop/${shopId}/online`, data);

// Shopkeeper views their shortage list
export const getShortageList = (shopId) =>
  api.get(`/shortage/shop/${shopId}`);

// Shopkeeper updates status of a shortage entry
export const updateShortageStatus = (entryId, status) =>
  api.patch(`/shortage/${entryId}/status?status=${status}`);

// Customer views their requests
export const getCustomerRequests = (customerId) =>
  api.get(`/shortage/customer/${customerId}`);

// Shopkeeper exports shortage list as CSV
export const exportShortageList = (shopId) =>
  api.get(`/shortage/shop/${shopId}/export`, { responseType: 'blob' });

export const createBill = (shopId, data) => api.post(`/billing/shop/${shopId}`, data);
export const getDailyBills = (shopId, date) => api.get(`/billing/shop/${shopId}/daily${date ? `?date=${date}` : ''}`);

export default api;
