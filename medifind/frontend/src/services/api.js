// src/services/api.js
// Central file for all API calls to Spring Boot backend

import axios from 'axios';

const BASE_URL = 'http://localhost:8081/api';

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
export const getNearbyShops = (lat, lng, filter = 'all') =>
  api.get(`/shops/nearby?lat=${lat}&lng=${lng}&filter=${filter}&radiusKm=3`);

export const getShop = (id) => api.get(`/shops/${id}`);
export const getCustomerPharmacy = (id) => api.get(`/customer/pharmacies/${id}`);
export const searchPharmacyMedicines = (shopId, query) =>
  api.get(`/customer/pharmacies/${shopId}/medicines`, { params: { query } });
export const checkPharmacyMedicine = (shopId, inventoryId) =>
  api.get(`/customer/pharmacies/${shopId}/medicines/${inventoryId}`);
export const identifyPharmacyMedicine = (shopId, formData) =>
  api.post(`/customer/pharmacies/${shopId}/identify`, formData);

export const addShop = (shopData) => api.post('/shops', shopData);

export const updateShop = (id, shopData) => api.put(`/shops/${id}`, shopData);

export const deleteShop = (id) => api.delete(`/shops/${id}`);

export const getAllShops = () => api.get('/shops/all');
export const getShopkeeperApplications = () => api.get('/admin/shopkeeper-applications');
export const reviewShopkeeperApplication = (id, status, lat, lng) => api.patch(`/admin/shopkeeper-applications/${id}`, { status, lat, lng });

export const toggleShopStatus = (shopId, open) =>
  api.patch(`/shops/${shopId}/status?open=${open}`);

// ─── SHORTAGE LIST ────────────────────────────────────────
export const addOfflineShortage = (shopId, data) =>
  api.post(`/shortage/shop/${shopId}/offline`, data);

export const requestMedicineOnline = (shopId, data) =>
  api.post(`/shortage/shop/${shopId}/online`, data);

export const getShortageList = (shopId) =>
  api.get(`/shortage/shop/${shopId}`);

export const updateShortageStatus = (entryId, status) =>
  api.patch(`/shortage/${entryId}/status?status=${status}`);

export const getCustomerRequests = (customerId) =>
  api.get(`/shortage/customer/${customerId}`);

export const exportShortageList = (shopId) =>
  api.get(`/shortage/shop/${shopId}/export`, { responseType: 'blob' });

// ─── BILLING ─────────────────────────────────────────────
export const createBill = (shopId, data) => api.post(`/billing/shop/${shopId}`, data);
export const createCustomerReceipt = (shopId, data) => api.post(`/billing/shop/${shopId}/receipt`, data);
export const getDailyBills = (shopId, date) => api.get(`/billing/shop/${shopId}/daily${date ? `?date=${date}` : ''}`);

// ─── AGENCIES ────────────────────────────────────────────
export const getAgencies = () => api.get('/agencies');
export const createAgency = (data) => api.post('/agencies', data);
export const updateAgency = (id, data) => api.put(`/agencies/${id}`, data);
export const deleteAgency = (id) => api.delete(`/agencies/${id}`);

// ─── AI BILL SCANNER ─────────────────────────────────────
export const scanBill = (shopId, formData) =>
  api.post(`/bills/ocr/invoice?shopId=${shopId}`, formData);

export const persistOcrInvoice = (shopId, agencyId, agencyName, document) =>
  api.post(`/bills/ocr/invoice/persist?shopId=${shopId}&agencyId=${agencyId}&agencyName=${encodeURIComponent(agencyName || '')}`, document);

export const getBillScans = (shopId) =>
  api.get(`/bills/scans/shop/${shopId}`);

export const getAgencyBills = (shopId) =>
  api.get(`/bills/agency/shop/${shopId}`);

// ─── INVOICES ───────────────────────────────────────────
export const getInvoices = (shopId) =>
  api.get(`/invoices/shop/${shopId}`);

export const getInvoice = (invoiceId) =>
  api.get(`/invoices/${invoiceId}`);

export const verifyInvoice = (invoiceId, data) =>
  api.patch(`/invoices/${invoiceId}/verify`, data);

// ─── INVENTORY ──────────────────────────────────────────
export const getInventoryByShop = (shopId) =>
  api.get(`/inventory/shop/${shopId}`);

export const searchInventory = (shopId, query) =>
  api.get(`/inventory/shop/${shopId}/search?query=${encodeURIComponent(query)}`);

export const getInventoryByAgencies = (shopId) =>
  api.get(`/inventory/shop/${shopId}/agencies`);

export const getInventorySummary = (shopId) =>
  api.get(`/inventory/shop/${shopId}/summary`);

export const getInventorySources = (shopId) =>
  api.get(`/inventory/shop/${shopId}/sources`);

export const clearShopInventory = (shopId) =>
  api.delete(`/inventory/shop/${shopId}`);

// ─── CHAT ────────────────────────────────────────────────
export const getChatHistory = (shopId, customerId) =>
  api.get(`/chat/history?shopId=${shopId}&customerId=${customerId}`);

export const sendChatMessage = (shopId, customerId, customerName, senderType, message) =>
  api.post(`/chat/send?shopId=${shopId}&customerId=${customerId}&customerName=${encodeURIComponent(customerName)}&senderType=${senderType}`, message);

export const getUnreadMessagesForShop = (shopId) =>
  api.get(`/chat/unread/shop?shopId=${shopId}`);

export const getUnreadMessagesForCustomer = (customerId) =>
  api.get(`/chat/unread/customer?customerId=${customerId}`);

// ─── NOTIFICATIONS ───────────────────────────────────────
export const getNotifications = (userId) =>
  api.get(`/notifications?userId=${userId}`);

export const getUnreadNotifications = (userId) =>
  api.get(`/notifications/unread?userId=${userId}`);

export const getNotificationCount = (userId) =>
  api.get(`/notifications/count?userId=${userId}`);

export const markNotificationRead = (id) =>
  api.patch(`/notifications/${id}/read`);

export const markAllNotificationsRead = (userId) =>
  api.patch(`/notifications/read-all?userId=${userId}`);

// ─── REVIEWS ─────────────────────────────────────────────
export const createReview = (data) => api.post('/reviews', data);
export const getShopReviews = (shopId) => api.get(`/reviews/shop/${shopId}`);
export const getShopAvgRating = (shopId) => api.get(`/reviews/avg/shop/${shopId}`);

// ─── SHOP AGENCIES ──────────────────────────────────────
export const getShopAgencies = (shopId) =>
  api.get(`/shop-agencies/shop/${shopId}`);

export const addShopAgency = (shopId, data) =>
  api.post(`/shop-agencies/shop/${shopId}`, data);

export const registerShopAgency = (shopId, name) =>
  api.post(`/shop-agencies/shop/${shopId}/register`, { name });

export const removeShopAgency = (shopId, agencyId) =>
  api.delete(`/shop-agencies/shop/${shopId}/agency/${agencyId}`);

export const updateShopAgencyStatus = (shopId, agencyId, isActive) =>
  api.patch(`/shop-agencies/shop/${shopId}/agency/${agencyId}`, { isActive });

// ─── ATTRIBUTE REGISTRY (admin) ──────────────────────────
export const getAttributeRegistry = () => api.get('/admin/attributes/registry');
export const createAttributeRegistry = (data) => api.post('/admin/attributes/registry', data);
export const updateAttributeRegistry = (id, data) => api.put(`/admin/attributes/registry/${id}`, data);
export const deleteAttributeRegistry = (id) => api.delete(`/admin/attributes/registry/${id}`);

export default api;
