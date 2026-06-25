/* ============================================================
   Cosmos PWA — Main Application Entry Point
   COSMOS Navigation: Connect · Organize · Social · Messenger · Orbits · Settings
   ============================================================ */

import { auth, onAuthStateChanged, signOut, db, doc, getDoc, setDoc, serverTimestamp, collection, query, where, orderBy, onSnapshot } from './firebase-config.js';
import router from './router.js';
import { renderAuth } from './pages/auth.js';
import { renderConnect } from './pages/connect.js';
import { renderOrganize } from './pages/organize.js';
import { renderSocial } from './pages/social.js';
import { renderMessenger } from './pages/messenger.js';
import { renderOrbits } from './pages/orbits.js';
import { renderSettings } from './pages/settings.js';
import { renderNotifications } from './pages/notifications.js';
import { renderEditProfile } from './pages/edit-profile.js';
import { renderHelpSupport } from './pages/help-support.js';

/* ── Global State ── */
window.cosmosApp = {
  user: null,
  userProfile: null,
};

/* ── Badge Listener Unsubscribers ── */
let unsubNotifBadge = null;
let unsubChatBadge = null;

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

/* ── Real-Time Badge Listeners ── */
function startBadgeListeners(uid) {
  // Clean up old listeners
  stopBadgeListeners();

  // Notification badge — count unread notifications
  const notifQuery = query(
    collection(db, 'notifications'),
    where('userId', '==', uid),
    where('isRead', '==', false)
  );

  unsubNotifBadge = onSnapshot(notifQuery, (snapshot) => {
    const count = snapshot.size;
    const badge = document.getElementById('notif-badge');
    if (badge) {
      if (count > 0) {
        badge.textContent = count > 99 ? '99+' : count;
        badge.style.display = 'flex';
      } else {
        badge.style.display = 'none';
      }
    }
  }, (err) => {
    console.warn('[Cosmos] Notification badge listener error:', err);
  });

  // Chat badge — count total unread messages across all connections
  const chatQuery = query(
    collection(db, 'connections'),
    where('members', 'array-contains', uid)
  );

  unsubChatBadge = onSnapshot(chatQuery, (snapshot) => {
    let totalUnread = 0;
    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      const unreadMap = data.unreadCountMap || {};
      totalUnread += (unreadMap[uid] || 0);
    });

    const badge = document.getElementById('chat-badge');
    if (badge) {
      if (totalUnread > 0) {
        badge.textContent = totalUnread > 99 ? '99+' : totalUnread;
        badge.style.display = 'flex';
      } else {
        badge.style.display = 'none';
      }
    }
  }, (err) => {
    console.warn('[Cosmos] Chat badge listener error:', err);
  });
}

function stopBadgeListeners() {
  if (unsubNotifBadge) {
    unsubNotifBadge();
    unsubNotifBadge = null;
  }
  if (unsubChatBadge) {
    unsubChatBadge();
    unsubChatBadge = null;
  }
  // Reset badges
  const notifBadge = document.getElementById('notif-badge');
  const chatBadge = document.getElementById('chat-badge');
  if (notifBadge) notifBadge.style.display = 'none';
  if (chatBadge) chatBadge.style.display = 'none';
}

/* ── Auth State Management ── */
async function ensureUserProfile(user) {
  const userRef = doc(db, 'users', user.uid);
  const snap = await getDoc(userRef);

  if (!snap.exists()) {
    await setDoc(userRef, {
      id: user.uid,
      name: user.displayName || user.email?.split('@')[0] || 'Builder',
      email: user.email?.toLowerCase() || '',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      isProfileComplete: false,
      primaryUserType: '',
      headline: '',
      role: '',
      company: '',
      avatarUrl: user.photoURL || '',
      location: '',
      bio: '',
      tags: [],
      goalStatement: '',
      longTermVision: '',
      lookingFor: [],
      isLinkedInConnected: false,
      membershipTier: 'EXPLORER',
      connectionsCount: 0,
      followersCount: 0,
      followingCount: 0,
      eventsAttended: 0,
      followUpsCompleted: 0,
      joinedCircles: [],
      pendingCircles: [],
      notificationNewMatches: true,
      notificationMessages: true,
      notificationAiSummaries: true,
      privacyProfileVisibility: true,
      privacyShowMutualConnections: true,
    });
  }

  const profileSnap = await getDoc(userRef);
  window.cosmosApp.userProfile = profileSnap.exists() ? profileSnap.data() : null;
}

function handleAuthState(user) {
  const splash = document.getElementById('splash-screen');
  
  if (user && !user.emailVerified) {
    signOut(auth).catch(err => console.error(err));
    window.cosmosApp.user = null;
    window.cosmosApp.userProfile = null;
    stopBadgeListeners();
    document.body.classList.add('auth-mode');
    router.navigate('/auth');
    setTimeout(() => {
      splash.classList.add('hidden');
    }, 800);
    return;
  }

  window.cosmosApp.user = user;

  if (user) {
    document.body.classList.remove('auth-mode');
    ensureUserProfile(user).catch((err) => {
      console.warn('Failed to sync user profile:', err);
    });

    // Start real-time badge listeners
    startBadgeListeners(user.uid);

    // If on auth page or root, navigate to connect
    const hash = window.location.hash;
    if (!hash || hash === '#/' || hash === '#/auth') {
      router.navigate('/connect');
    }
  } else {
    window.cosmosApp.userProfile = null;
    stopBadgeListeners();
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

  // Register COSMOS routes
  router.addRoute('/auth', renderAuth);
  router.addRoute('/connect', renderConnect);         // C
  router.addRoute('/organize', renderOrganize);        // O
  router.addRoute('/social', renderSocial);            // S
  router.addRoute('/messenger', renderMessenger);      // M
  router.addRoute('/orbits', renderOrbits);            // O
  router.addRoute('/settings', renderSettings);         // S

  // Sub-page routes
  router.addRoute('/notifications', renderNotifications);
  router.addRoute('/edit-profile', renderEditProfile);
  router.addRoute('/help-support', renderHelpSupport);
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
