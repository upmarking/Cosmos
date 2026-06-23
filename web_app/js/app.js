/* ============================================================
   Cosmos PWA — Main Application Entry Point
   ============================================================ */

import { auth, onAuthStateChanged, signOut, db, collection, query, where, onSnapshot } from './firebase-config.js';
import router from './router.js';
import { renderAuth } from './pages/auth.js';
import { renderVerifyEmail } from './pages/verify-email.js';
import { renderConnect } from './pages/connect.js';
import { renderEvents } from './pages/events.js';
import { renderConversations } from './pages/conversations.js';
import { renderCommunities } from './pages/communities.js';
import { renderSocial } from './pages/social.js';
import { renderProfile } from './pages/profile.js';
import { renderNotifications } from './pages/notifications.js';
import { renderSettings } from './pages/settings.js';

/* ── Global State ── */
window.cosmosApp = {
  user: null,
  userProfile: null,
};

/* ── Toast System ── */
export function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  
  const icons = { success: '✓', error: '✕', info: 'ℹ' };
  toast.innerHTML = `<span>${icons[type] || 'ℹ'}</span> ${message}`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.classList.add('toast-exit');
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}

/* ── Loading Helper ── */
export function showLoading(container) {
  container.innerHTML = '<div class="loading-spinner"></div>';
}

/* ── Skeleton Helper ── */
export function createSkeleton(width, height) {
  return `<div class="skeleton" style="width:${width};height:${height};"></div>`;
}

/* ── Global Real-Time Counters ── */
let unsubGlobalNotifs = null;
let unsubGlobalChats = null;

function startGlobalListeners(uid) {
  stopGlobalListeners();

  // 1. Unread notifications
  const notifQuery = query(
    collection(db, 'notifications'),
    where('userId', '==', uid),
    where('isRead', '==', false)
  );
  unsubGlobalNotifs = onSnapshot(notifQuery, (snap) => {
    const badge = document.getElementById('notif-badge');
    if (badge) {
      const count = snap.size;
      if (count > 0) {
        badge.style.display = 'flex';
        badge.textContent = count;
      } else {
        badge.style.display = 'none';
      }
    }
  }, (err) => {
    console.error('[Cosmos App] Error in global notifications listener:', err);
  });

  // 2. Unread connection chats
  const chatQuery = query(
    collection(db, 'connections'),
    where('members', 'array-contains', uid)
  );
  unsubGlobalChats = onSnapshot(chatQuery, (snap) => {
    const badge = document.getElementById('chat-badge');
    if (badge) {
      let unreadSum = 0;
      snap.forEach(docSnap => {
        const data = docSnap.data();
        if (data.unreadCountMap && typeof data.unreadCountMap[uid] === 'number') {
          unreadSum += data.unreadCountMap[uid];
        }
      });
      if (unreadSum > 0) {
        badge.style.display = 'flex';
        badge.textContent = unreadSum;
      } else {
        badge.style.display = 'none';
      }
    }
  }, (err) => {
    console.error('[Cosmos App] Error in global chat listener:', err);
  });
}

function stopGlobalListeners() {
  if (unsubGlobalNotifs) {
    unsubGlobalNotifs();
    unsubGlobalNotifs = null;
  }
  if (unsubGlobalChats) {
    unsubGlobalChats();
    unsubGlobalChats = null;
  }
  const notifBadge = document.getElementById('notif-badge');
  const chatBadge = document.getElementById('chat-badge');
  if (notifBadge) notifBadge.style.display = 'none';
  if (chatBadge) chatBadge.style.display = 'none';
}

/* ── Auth State Management ── */
function handleAuthState(user) {
  const splash = document.getElementById('splash-screen');
  
  if (user && !user.emailVerified) {
    window.cosmosApp.user = user;
    stopGlobalListeners();
    document.body.classList.add('auth-mode');
    router.navigate('/verify-email');
    setTimeout(() => {
      splash.classList.add('hidden');
    }, 800);
    return;
  }

  window.cosmosApp.user = user;

  if (user) {
    document.body.classList.remove('auth-mode');
    startGlobalListeners(user.uid);
    // If on auth page or root, navigate to connect
    const hash = window.location.hash;
    if (!hash || hash === '#/' || hash === '#/auth' || hash === '#/verify-email') {
      router.navigate('/connect');
    }
  } else {
    stopGlobalListeners();
    document.body.classList.add('auth-mode');
    router.navigate('/auth');
  }

  // Hide splash after auth check
  setTimeout(() => {
    splash.classList.add('hidden');
  }, 800);
}

/* ── Initialize Router ── */
function initRouter() {
  router.setOutlet('#app');

  // Route guard
  router.setGuard(async (path) => {
    const publicRoutes = ['/auth'];
    if (!publicRoutes.includes(path) && !window.cosmosApp.user) {
      router.navigate('/auth');
      return false;
    }
    if (window.cosmosApp.user && !window.cosmosApp.user.emailVerified && path !== '/verify-email') {
      router.navigate('/verify-email');
      return false;
    }
    return true;
  });

  // Register routes
  router.addRoute('/auth', renderAuth);
  router.addRoute('/verify-email', renderVerifyEmail);
  router.addRoute('/connect', renderConnect);
  router.addRoute('/events', renderEvents);
  router.addRoute('/conversations', renderConversations);
  router.addRoute('/communities', renderCommunities);
  router.addRoute('/social', renderSocial);
  router.addRoute('/profile', renderProfile);
  router.addRoute('/notifications', renderNotifications);
  router.addRoute('/settings', renderSettings);
}

/* ── PWA Install Prompt ── */
let deferredPrompt = null;
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredPrompt = e;
  showInstallPrompt();
});

function showInstallPrompt() {
  // Don't show if already installed or dismissed
  if (window.matchMedia('(display-mode: standalone)').matches) return;
  if (sessionStorage.getItem('cosmos-install-dismissed')) return;

  const prompt = document.createElement('div');
  prompt.className = 'install-prompt';
  prompt.innerHTML = `
    <div style="font-size:1.5rem;">📱</div>
    <div class="install-prompt-text">
      <div class="install-prompt-title">Install Cosmos</div>
      <div class="install-prompt-desc">Add to your home screen for the best experience</div>
    </div>
    <button class="btn btn-primary btn-sm" id="install-btn">Install</button>
    <button class="install-prompt-close" id="install-dismiss">✕</button>
  `;
  document.body.appendChild(prompt);

  document.getElementById('install-btn').addEventListener('click', async () => {
    if (deferredPrompt) {
      deferredPrompt.prompt();
      const result = await deferredPrompt.userChoice;
      if (result.outcome === 'accepted') {
        showToast('Cosmos installed!', 'success');
      }
      deferredPrompt = null;
    }
    prompt.remove();
  });

  document.getElementById('install-dismiss').addEventListener('click', () => {
    sessionStorage.setItem('cosmos-install-dismissed', 'true');
    prompt.remove();
  });
}

/* ── Boot ── */
function init() {
  initRouter();
  onAuthStateChanged(auth, handleAuthState);
}

init();
