/* ============================================================
   Cosmos PWA — Notifications Page
   ============================================================ */

import { auth, db, collection, doc, query, where, orderBy, onSnapshot, updateDoc } from '../firebase-config.js';
import { showToast } from '../app.js';

function formatTime(timestamp) {
  if (!timestamp) return 'Now';
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
  const diff = Date.now() - date.getTime();
  if (diff < 60000) return 'now';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
  return date.toLocaleDateString([], { day: 'numeric', month: 'short' });
}

export async function renderNotifications(outlet) {
  const user = auth.currentUser;
  if (!user) return;

  // Clean up any existing listener
  if (window.cosmosApp._notifUnsubscribe) {
    window.cosmosApp._notifUnsubscribe();
    window.cosmosApp._notifUnsubscribe = null;
  }

  outlet.innerHTML = `
    <div class="notifications-page page">
      <div class="page-header" style="display:flex;align-items:center;justify-content:space-between;">
        <div>
          <h1 class="page-title">Notifications</h1>
          <p class="page-subtitle" id="notif-subtitle">Loading...</p>
        </div>
        <button class="btn btn-ghost btn-sm" id="mark-all-read" style="display:none;">Mark all read</button>
      </div>
      <div class="notif-list stagger" id="notif-list">
        <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
      </div>
    </div>
  `;

  const q = query(
    collection(db, 'notifications'),
    where('userId', '==', user.uid),
    orderBy('timestamp', 'desc')
  );

  window.cosmosApp._notifUnsubscribe = onSnapshot(q, (snapshot) => {
    const list = [];
    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      list.push({
        id: docSnap.id,
        type: data.type || '',
        title: data.title || '',
        body: data.body || '',
        isRead: data.isRead || false,
        timestamp: data.timestamp,
        actionId: data.actionId || ''
      });
    });

    const unreadCount = list.filter(n => !n.isRead).length;

    // Update subtitle
    const subtitle = outlet.querySelector('#notif-subtitle');
    if (subtitle) {
      subtitle.textContent = `${unreadCount} unread`;
    }

    // Mark all read button
    const markAllBtn = outlet.querySelector('#mark-all-read');
    if (markAllBtn) {
      markAllBtn.style.display = unreadCount > 0 ? 'block' : 'none';
    }

    // Render list
    const listContainer = outlet.querySelector('#notif-list');
    if (listContainer) {
      listContainer.innerHTML = renderNotifItems(list);
      attachNotifListeners(outlet, list);
    }
  }, (error) => {
    console.error('[Cosmos Notifications] Error in snapshot:', error);
    const listContainer = outlet.querySelector('#notif-list');
    if (listContainer) {
      listContainer.innerHTML = `
        <div class="empty-state">
          <div class="empty-state-icon">⚠️</div>
          <h3 class="empty-state-title">Error Loading Notifications</h3>
          <p class="empty-state-desc">${error.message}</p>
        </div>
      `;
    }
  });
}

function renderNotifItems(notifications) {
  if (!notifications.length) {
    return `
      <div class="empty-state">
        <div class="empty-state-icon">🔔</div>
        <h3 class="empty-state-title">All Caught Up!</h3>
        <p class="empty-state-desc">No new notifications. Keep building those connections!</p>
      </div>
    `;
  }

  const icons = {
    CONNECTION_REQUEST: '🎯',
    CONNECTION_ACCEPTED: '🎉',
    EVENT_REMINDER: '📅',
    COMMUNITY_ANNOUNCEMENT: '🌐',
    MESSAGE: '💬'
  };

  const iconBgs = {
    CONNECTION_REQUEST: 'rgba(167,139,250,0.15)',
    CONNECTION_ACCEPTED: 'rgba(52,211,153,0.15)',
    EVENT_REMINDER: 'rgba(96,165,250,0.15)',
    COMMUNITY_ANNOUNCEMENT: 'rgba(244,114,182,0.15)',
    MESSAGE: 'rgba(96,165,250,0.15)'
  };

  return notifications.map((n, i) => {
    const icon = icons[n.type] || '🔔';
    const bg = iconBgs[n.type] || 'rgba(255,255,255,0.05)';
    const text = `<strong>${n.title}</strong>: ${n.body}`;
    const time = formatTime(n.timestamp);

    return `
      <div class="notif-item ${!n.isRead ? 'unread' : ''} anim-fade-up" data-id="${n.id}" style="animation-delay:${i * 0.04}s;">
        <div class="notif-icon" style="background:${bg};">${icon}</div>
        <div class="notif-content">
          <div class="notif-text">${text}</div>
          <div class="notif-time">${time}</div>
        </div>
        ${!n.isRead ? '<div class="notif-dot"></div>' : ''}
      </div>
    `;
  }).join('');
}

function attachNotifListeners(outlet, notifications) {
  // Mark all read button
  const markAllBtn = outlet.querySelector('#mark-all-read');
  if (markAllBtn) {
    const newBtn = markAllBtn.cloneNode(true);
    markAllBtn.replaceWith(newBtn);
    newBtn.addEventListener('click', async () => {
      const unread = notifications.filter(n => !n.isRead);
      try {
        await Promise.all(unread.map(n => 
          updateDoc(doc(db, 'notifications', n.id), { isRead: true })
        ));
        showToast('All notifications marked as read', 'success');
      } catch (e) {
        showToast('Failed to mark notifications read', 'error');
      }
    });
  }

  // Individual notification items
  outlet.querySelectorAll('.notif-item').forEach(item => {
    item.addEventListener('click', async () => {
      const id = item.dataset.id;
      const notif = notifications.find(n => n.id === id);
      if (notif) {
        if (!notif.isRead) {
          try {
            await updateDoc(doc(db, 'notifications', id), { isRead: true });
          } catch (e) {
            console.error('[Cosmos Notifications] Error marking read:', e);
          }
        }

        // Navigate based on type
        switch (notif.type) {
          case 'CONNECTION_REQUEST':
            window.location.hash = '#/settings';
            break;
          case 'CONNECTION_ACCEPTED':
            window.location.hash = '#/conversations';
            break;
          case 'EVENT_REMINDER':
            window.location.hash = '#/events';
            break;
          case 'COMMUNITY_ANNOUNCEMENT':
            window.location.hash = '#/communities';
            break;
          case 'MESSAGE':
            window.location.hash = '#/conversations';
            break;
          default:
            showToast('Notification viewed', 'info');
        }
      }
    });
  });
}

