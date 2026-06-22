/* ============================================================
   Cosmos PWA — Notifications Page
   ============================================================ */

import { showToast } from '../app.js';

const demoNotifications = [
  { id: '1', type: 'match', icon: '🎯', iconBg: 'rgba(167,139,250,0.15)', text: '<strong>Sarah Chen</strong> matched with you! Start a conversation.', time: '2 mins ago', unread: true },
  { id: '2', type: 'message', icon: '💬', iconBg: 'rgba(96,165,250,0.15)', text: '<strong>Marcus Rivera</strong> sent you a message: "I\'d love to hear more..."', time: '1 hour ago', unread: true },
  { id: '3', type: 'event', icon: '📅', iconBg: 'rgba(52,211,153,0.15)', text: 'Reminder: <strong>Founders Speed Network</strong> starts in 2 hours', time: '2 hours ago', unread: true },
  { id: '4', type: 'ai', icon: '🤖', iconBg: 'rgba(244,114,182,0.15)', text: 'AI summary ready for your meeting with <strong>Aisha Patel</strong>', time: '3 hours ago', unread: false },
  { id: '5', type: 'endorsement', icon: '⭐', iconBg: 'rgba(251,191,36,0.15)', text: '<strong>Elena Volkov</strong> endorsed your <strong>Product Thinking</strong> skill', time: '5 hours ago', unread: false },
  { id: '6', type: 'intro', icon: '🤝', iconBg: 'rgba(45,212,191,0.15)', text: '<strong>James Okafor</strong> accepted your warm introduction request', time: 'Yesterday', unread: false },
  { id: '7', type: 'community', icon: '🌐', iconBg: 'rgba(167,139,250,0.15)', text: 'New post in <strong>Founders Building in AI</strong>: "AI agents are the new SaaS..."', time: 'Yesterday', unread: false },
  { id: '8', type: 'follow_up', icon: '⏰', iconBg: 'rgba(248,113,113,0.15)', text: 'Follow-up reminder: Send deck to <strong>Marcus Rivera</strong>', time: '2 days ago', unread: false },
  { id: '9', type: 'event', icon: '🎉', iconBg: 'rgba(52,211,153,0.15)', text: 'You\'ve been invited to <strong>Investor-Founder Match</strong> (invite-only)', time: '2 days ago', unread: false },
  { id: '10', type: 'match', icon: '✨', iconBg: 'rgba(167,139,250,0.15)', text: '<strong>Kai Tanaka</strong> wants to connect — 82% match score', time: '3 days ago', unread: false },
];

export async function renderNotifications(outlet) {
  const unreadCount = demoNotifications.filter(n => n.unread).length;

  outlet.innerHTML = `
    <div class="notifications-page page">
      <div class="page-header" style="display:flex;align-items:center;justify-content:space-between;">
        <div>
          <h1 class="page-title">Notifications</h1>
          <p class="page-subtitle">${unreadCount} unread</p>
        </div>
        <button class="btn btn-ghost btn-sm" id="mark-all-read">Mark all read</button>
      </div>
      <div class="notif-list stagger" id="notif-list">
        ${renderNotifItems(demoNotifications)}
      </div>
    </div>
  `;

  // Mark all read
  outlet.querySelector('#mark-all-read')?.addEventListener('click', () => {
    demoNotifications.forEach(n => n.unread = false);
    outlet.querySelectorAll('.notif-item.unread').forEach(item => item.classList.remove('unread'));
    outlet.querySelectorAll('.notif-dot').forEach(dot => dot.style.display = 'none');
    outlet.querySelector('.page-subtitle').textContent = '0 unread';
    // Update badge in top bar
    const badge = document.getElementById('notif-badge');
    if (badge) badge.style.display = 'none';
    showToast('All notifications marked as read', 'success');
  });

  // Individual notification clicks
  outlet.querySelectorAll('.notif-item').forEach(item => {
    item.addEventListener('click', () => {
      const id = item.dataset.id;
      const notif = demoNotifications.find(n => n.id === id);
      if (notif) {
        notif.unread = false;
        item.classList.remove('unread');
        const dot = item.querySelector('.notif-dot');
        if (dot) dot.style.display = 'none';

        // Navigate based on type
        switch (notif.type) {
          case 'match': window.location.hash = '#/connect'; break;
          case 'message': window.location.hash = '#/conversations'; break;
          case 'event': window.location.hash = '#/events'; break;
          case 'community': window.location.hash = '#/communities'; break;
          default: showToast('Notification viewed', 'info');
        }
      }
    });
  });

  // Update unread badge
  const badge = document.getElementById('notif-badge');
  if (badge) {
    if (unreadCount > 0) {
      badge.style.display = 'flex';
      badge.textContent = unreadCount;
    } else {
      badge.style.display = 'none';
    }
  }
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

  return notifications.map((n, i) => `
    <div class="notif-item ${n.unread ? 'unread' : ''} anim-fade-up" data-id="${n.id}" style="animation-delay:${i * 0.04}s;">
      <div class="notif-icon" style="background:${n.iconBg};">${n.icon}</div>
      <div class="notif-content">
        <div class="notif-text">${n.text}</div>
        <div class="notif-time">${n.time}</div>
      </div>
      ${n.unread ? '<div class="notif-dot"></div>' : ''}
    </div>
  `).join('');
}
