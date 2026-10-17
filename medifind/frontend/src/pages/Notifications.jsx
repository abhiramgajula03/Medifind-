import React, { useState, useRef } from 'react';
import { getNotifications, getNotificationCount, markNotificationRead, markAllNotificationsRead } from '../services/api';

const TYPE_LABELS = {
  SHOP_UPDATE: 'Shop',
  MEDICINE_AVAILABLE: 'Medicine',
  SHORTAGE_UPDATE: 'Shortage',
  AGENCY_UPDATE: 'Agency',
  SYSTEM: 'System',
};

const TYPE_CLASS = {
  SHOP_UPDATE: 'shop',
  MEDICINE_AVAILABLE: 'medicine',
  SHORTAGE_UPDATE: 'shortage',
  AGENCY_UPDATE: 'agency',
  SYSTEM: 'system',
};

function timeAgo(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  const diff = (Date.now() - date.getTime()) / 1000;
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  if (diff < 604800) return `${Math.floor(diff / 86400)}d ago`;
  return date.toLocaleDateString();
}

export default function Notifications() {
  const userId = localStorage.getItem('userId');
  const [items, setItems] = useState([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');

  const load = async () => {
    if (!userId) return;
    setLoading(true);
    try {
      const [listRes, countRes] = await Promise.all([
        getNotifications(userId),
        getNotificationCount(userId),
      ]);
      setItems(Array.isArray(listRes.data) ? listRes.data : []);
      setUnread(countRes.data || 0);
    } catch {
      setMessage('Could not load notifications.');
    } finally {
      setLoading(false);
    }
  };

  const open = async (n) => {
    if (n.isRead) return;
    try {
      await markNotificationRead(n.id);
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, isRead: true } : x)));
      setUnread((c) => Math.max(0, c - 1));
    } catch { /* ignore */ }
  };

  const markAll = async () => {
    try {
      await markAllNotificationsRead(userId);
      setItems((prev) => prev.map((x) => ({ ...x, isRead: true })));
      setUnread(0);
      setMessage('All notifications marked as read.');
    } catch {
      setMessage('Could not mark notifications as read.');
    }
  };

  return (
    <main className="portal-page">
      <header className="portal-heading">
        <div>
          <span>YOUR ACTIVITY</span>
          <h1>Notifications</h1>
          <p>Stay up to date with shop, medicine, and shortage updates.</p>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <span className="metric"><b>{unread}</b><small>Unread</small></span>
          <button className="primary-action" onClick={markAll} disabled={unread === 0}>Mark all read</button>
        </div>
      </header>

      {message && <div className="portal-message">{message}</div>}

      {loading ? (
        <div className="portal-message">Loading notifications...</div>
      ) : items.length === 0 ? (
        <div className="empty-state">
          <span>🔔</span>
          <strong>No notifications yet</strong>
          <small>We'll let you know when something happens.</small>
        </div>
      ) : (
        <section className="notification-list">
          {items.map((n) => (
            <article
              key={n.id}
              className={`notification-row ${n.isRead ? '' : 'unread'}`}
              onClick={() => open(n)}
            >
              <div className="notification-icon">
                <span className={`notif-type ${TYPE_CLASS[n.type] || 'system'}`}>
                  {TYPE_LABELS[n.type] || 'System'}
                </span>
              </div>
              <div className="notification-copy">
                <div className="notification-title-line">
                  <h3>{n.title}</h3>
                  {!n.isRead && <span className="unread-dot" />}
                </div>
                <p>{n.message}</p>
                <small>{timeAgo(n.createdAt)}</small>
              </div>
            </article>
          ))}
        </section>
      )}
    </main>
  );
}
