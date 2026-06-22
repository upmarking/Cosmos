/* ============================================================
   Cosmos PWA — Main Application Entry Point
   ============================================================ */

import { auth, onAuthStateChanged } from './firebase-config.js';
import router from './router.js';
import { renderAuth } from './pages/auth.js';
import { renderConnect } from './pages/connect.js';
import { renderEvents } from './pages/events.js';
import { renderConversations } from './pages/conversations.js';
import { renderCommunities } from './pages/communities.js';
import { renderSocial } from './pages/social.js';
import { renderProfile } from './pages/profile.js';
import { renderNotifications } from './pages/notifications.js';

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

/* ── Auth State Management ── */
function handleAuthState(user) {
  const splash = document.getElementById('splash-screen');
  
  window.cosmosApp.user = user;

  if (user) {
    document.body.classList.remove('auth-mode');
    // If on auth page or root, navigate to connect
    const hash = window.location.hash;
    if (!hash || hash === '#/' || hash === '#/auth') {
      router.navigate('/connect');
    }
  } else {
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
    return true;
  });

  // Register routes
  router.addRoute('/auth', renderAuth);
  router.addRoute('/connect', renderConnect);
  router.addRoute('/events', renderEvents);
  router.addRoute('/conversations', renderConversations);
  router.addRoute('/communities', renderCommunities);
  router.addRoute('/social', renderSocial);
  router.addRoute('/profile', renderProfile);
  router.addRoute('/notifications', renderNotifications);
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
