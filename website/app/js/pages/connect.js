/* ============================================================
   Cosmos PWA — Connect Page (Swipe Discovery)
   Real-time Firestore integration with premium profile cards
   ============================================================ */

import {
  auth, db, collection, getDocs, query, where, limit,
  doc, getDoc, addDoc, setDoc, serverTimestamp, onSnapshot
} from '../firebase-config.js';
import { showToast } from '../app.js';

/* ── State ── */
let profiles = [];
let currentIndex = 0;
let startX = 0;
let currentX = 0;
let isDragging = false;
let isLoading = true;
let loadError = null;
let unsubscribeSnapshot = null;    // real-time listener cleanup

/* ── Firestore Data Fetching ── */

/**
 * Fetches the discovery deck from Firestore:
 * 1. Gets current user doc (for tag-based matching)
 * 2. Gets swiped UIDs to exclude
 * 3. Loads all candidate users, filters & ranks
 */
async function fetchDiscoveryDeck() {
  const user = auth.currentUser;
  if (!user) throw new Error('Not authenticated');

  const uid = user.uid;

  // 1. Fetch current user profile
  const meSnap = await getDoc(doc(db, 'users', uid));
  const me = meSnap.exists() ? meSnap.data() : {};

  // 2. Fetch already-swiped UIDs
  const swipesSnap = await getDocs(
    query(collection(db, 'swipes'), where('likerId', '==', uid))
  );
  const swipedIds = new Set();
  swipesSnap.forEach(d => {
    const likedId = d.data().likedId;
    if (likedId) swipedIds.add(likedId);
  });

  // 3. Fetch all users
  const allUsersSnap = await getDocs(collection(db, 'users'));
  const candidates = [];

  allUsersSnap.forEach(d => {
    if (d.id === uid) return;
    if (swipedIds.has(d.id)) return;
    const data = d.data();
    if (data.isRestricted === true) return;
    if (d.id.startsWith('mock_user_')) return;

    candidates.push({ id: d.id, ...data });
  });

  // 4. Compute match scores (mirrors Android DiscoveryViewModel)
  const myTags = new Set((me.tags || []).map(t => t.toLowerCase()));
  const myGoalWords = new Set(
    (me.goalStatement || '').toLowerCase().split(/\s+/).filter(Boolean)
  );
  const myLookingFor = new Set(
    (me.lookingFor || []).map(l => l.toLowerCase())
  );

  const scored = candidates.map(candidate => {
    const cTags = (candidate.tags || []).map(t => t.toLowerCase());
    const sharedTags = cTags.filter(t => myTags.has(t)).length;

    let hasSharedGoal = false;
    if (myGoalWords.size > 0 && candidate.goalStatement) {
      const cGoalWords = candidate.goalStatement.toLowerCase().split(/\s+/);
      hasSharedGoal = cGoalWords.some(w => myGoalWords.has(w));
    }

    let score = 0;
    score += sharedTags * 10;
    if (hasSharedGoal) score += 15;
    if (candidate.isLinkedInConnected) score += 5;
    score += (candidate.mutualConnectionsCount || 0) * 2;

    const cLookingFor = (candidate.lookingFor || []).map(l => l.toLowerCase());
    const sharedLF = cLookingFor.filter(l => myLookingFor.has(l)).length;
    score += sharedLF * 8;

    return { profile: candidate, score };
  });

  // Include all users (even zero-score ones) — just sort by score descending
  scored.sort((a, b) => b.score - a.score);

  return scored.map(s => {
    const p = s.profile;
    const initials = (p.name || '')
      .split(' ')
      .map(w => w.charAt(0))
      .join('')
      .toUpperCase()
      .slice(0, 2);

    return {
      id: p.id,
      displayName: p.name || 'Unknown User',
      headline: p.headline || p.role || '',
      company: p.company || '',
      bio: p.bio || '',
      tags: p.tags || [],
      avatarUrl: p.avatarUrl || '',
      isLinkedInConnected: p.isLinkedInConnected || false,
      mutualConnections: p.mutualConnectionsCount || 0,
      membershipTier: p.membershipTier || 'EXPLORER',
      location: p.location || '',
      matchScore: Math.min(99, Math.max(50, 50 + s.score)),
      initials,
      lookingFor: p.lookingFor || [],
      primaryUserType: p.primaryUserType || '',
    };
  });
}

/**
 * Records a swipe (left = skip, right = connect) in Firestore
 */
async function recordSwipe(likedId, action) {
  const user = auth.currentUser;
  if (!user) return;

  const swipeId = `${user.uid}_${likedId}`;
  await setDoc(doc(db, 'swipes', swipeId), {
    likerId: user.uid,
    likedId: likedId,
    action: action,
    timestamp: serverTimestamp()
  });

  // If "connect" → also send a connection request
  if (action === 'right') {
    await addDoc(collection(db, 'connectionRequests'), {
      senderId: user.uid,
      receiverId: likedId,
      status: 'PENDING',
      createdAt: serverTimestamp()
    });
  }
}

/* ── Monthly Connection Counter (from Firestore connections) ── */
async function getMonthlyConnectionsCount() {
  const user = auth.currentUser;
  if (!user) return 0;

  try {
    const now = new Date();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);

    const connSnap = await getDocs(
      query(collection(db, 'connections'), where('members', 'array-contains', user.uid))
    );
    let count = 0;
    connSnap.forEach(d => {
      const ts = d.data().createdAt;
      if (ts && ts.toDate && ts.toDate() >= startOfMonth) count++;
    });
    return count;
  } catch {
    return parseInt(localStorage.getItem('cosmos-connections-month') || '0');
  }
}

/* ── Render ── */

export async function renderConnect(outlet) {
  currentIndex = 0;
  profiles = [];
  isLoading = true;
  loadError = null;

  // Clean up any previous real-time listener
  if (unsubscribeSnapshot) {
    unsubscribeSnapshot();
    unsubscribeSnapshot = null;
  }

  // Show loading state
  outlet.innerHTML = renderLoadingState();

  try {
    // Fetch connection count + discovery deck in parallel
    const [monthlyCount, deck] = await Promise.all([
      getMonthlyConnectionsCount(),
      fetchDiscoveryDeck()
    ]);

    profiles = deck;
    isLoading = false;

    const connectionsLeft = 10 - monthlyCount;

    outlet.innerHTML = `
      <div class="connect-page" id="connect-page">
        <div class="connect-header">
          <div class="page-header" style="margin-bottom:0;">
            <h1 class="page-title">Discover</h1>
            <p class="page-subtitle">Find your next meaningful connection</p>
          </div>
          <div class="connect-counter" id="connect-counter">
            <strong>${Math.max(0, connectionsLeft)}</strong> / 10 left
          </div>
        </div>
        <div class="swipe-area" id="swipe-area">
          ${profiles.length > 0 ? renderProfileCard(profiles[0]) : renderEmptyState()}
        </div>
        ${profiles.length > 0 ? renderSwipeActions() : ''}
      </div>
    `;

    if (profiles.length > 0) {
      attachSwipeListeners(outlet);
    }

  } catch (err) {
    console.error('[Cosmos Connect] Error loading deck:', err);
    isLoading = false;
    loadError = err;
    outlet.innerHTML = renderErrorState(err.message);
  }
}

function renderLoadingState() {
  return `
    <div class="connect-page">
      <div class="connect-header">
        <div class="page-header" style="margin-bottom:0;">
          <h1 class="page-title">Discover</h1>
          <p class="page-subtitle">Find your next meaningful connection</p>
        </div>
      </div>
      <div class="swipe-area" id="swipe-area">
        <div class="profile-card-skeleton">
          <div class="skeleton-pulse skeleton-avatar-lg"></div>
          <div class="skeleton-pulse skeleton-name"></div>
          <div class="skeleton-pulse skeleton-role"></div>
          <div class="skeleton-tags-row">
            <div class="skeleton-pulse skeleton-tag"></div>
            <div class="skeleton-pulse skeleton-tag"></div>
            <div class="skeleton-pulse skeleton-tag"></div>
          </div>
          <div class="skeleton-pulse skeleton-bio"></div>
          <div class="skeleton-pulse skeleton-bio-short"></div>
          <div class="connect-loading-orbit">
            <div class="orbit-ring"></div>
            <div class="orbit-dot"></div>
          </div>
          <p class="connect-loading-text">Finding people for you…</p>
        </div>
      </div>
    </div>
  `;
}

function renderErrorState(message) {
  return `
    <div class="connect-page">
      <div class="connect-header">
        <div class="page-header" style="margin-bottom:0;">
          <h1 class="page-title">Discover</h1>
          <p class="page-subtitle">Find your next meaningful connection</p>
        </div>
      </div>
      <div class="swipe-area" id="swipe-area">
        <div class="card" style="height:100%;display:flex;align-items:center;justify-content:center;">
          <div class="empty-state">
            <div class="empty-state-icon">⚠️</div>
            <h3 class="empty-state-title">Something Went Wrong</h3>
            <p class="empty-state-desc">${message || 'Unable to load profiles. Please check your connection and try again.'}</p>
            <button class="btn btn-primary" id="retry-btn" style="margin-top: 1rem;">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
              Try Again
            </button>
          </div>
        </div>
      </div>
    </div>
  `;
}

function renderEmptyState() {
  return `
    <div class="card" style="height:100%;display:flex;align-items:center;justify-content:center;">
      <div class="empty-state">
        <div class="empty-state-icon">🌟</div>
        <h3 class="empty-state-title">You're All Caught Up!</h3>
        <p class="empty-state-desc">Check back later for new profiles that match your interests.</p>
      </div>
    </div>
  `;
}

function renderSwipeActions() {
  return `
    <div class="swipe-actions" id="swipe-actions">
      <button class="swipe-btn swipe-btn-skip" id="btn-skip" aria-label="Pass">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
      </button>
      <button class="swipe-btn swipe-btn-connect" id="btn-connect" aria-label="Connect">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
      </button>
      <button class="swipe-btn swipe-btn-info" id="btn-info" aria-label="More info">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
      </button>
    </div>
  `;
}

/* ── Profile Card Renderer ── */

function renderProfileCard(profile) {
  if (!profile) return renderEmptyState();

  const avatarColors = [
    'linear-gradient(135deg,#7c3aed,#a78bfa)',
    'linear-gradient(135deg,#2563eb,#60a5fa)',
    'linear-gradient(135deg,#db2777,#f472b6)',
    'linear-gradient(135deg,#059669,#34d399)',
    'linear-gradient(135deg,#d97706,#fbbf24)',
    'linear-gradient(135deg,#7c3aed,#f472b6)',
  ];
  const bg = avatarColors[currentIndex % avatarColors.length];

  // Membership tier badge
  const tierConfig = {
    EXPLORER: { label: 'Explorer', cls: 'tier-explorer' },
    MEMBER: { label: 'Member', cls: 'tier-member' },
    INNER_CIRCLE: { label: 'Inner Circle', cls: 'tier-inner' },
    FOUNDER: { label: 'Founder', cls: 'tier-founder' },
  };
  const tier = tierConfig[profile.membershipTier] || tierConfig.EXPLORER;

  // Avatar: show real image if available, otherwise initials
  const avatarContent = profile.avatarUrl
    ? `<img src="${profile.avatarUrl}" alt="${profile.displayName}" style="width:100%;height:100%;object-fit:cover;border-radius:50%;" onerror="this.style.display='none';this.nextElementSibling.style.display='flex';" /><span class="avatar-initials-fallback" style="display:none;width:100%;height:100%;align-items:center;justify-content:center;">${profile.initials}</span>`
    : profile.initials;

  // Tags rendered as styled badges
  const tagsHtml = (profile.tags || []).slice(0, 5).map((t, i) => {
    const cls = ['tag', 'tag-blue', 'tag-pink', 'tag-green', 'tag-amber'][i % 5];
    return `<span class="${cls}">${t}</span>`;
  }).join('');

  // "Looking for" pills
  const lookingForHtml = (profile.lookingFor || []).slice(0, 3).map(l => {
    return `<span class="looking-for-pill">🔍 ${l}</span>`;
  }).join('');

  // LinkedIn badge
  const linkedInBadge = profile.isLinkedInConnected
    ? `<div class="profile-card-linkedin">
         <svg width="14" height="14" viewBox="0 0 24 24" fill="#0a66c2"><path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 0 1-2.063-2.065 2.064 2.064 0 1 1 2.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/></svg>
         LinkedIn Verified
       </div>`
    : '';

  // Location line
  const locationLine = profile.location
    ? `<div class="profile-card-location">
         <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
         ${profile.location}
       </div>`
    : '';

  // User type badge
  const userTypeBadge = profile.primaryUserType
    ? `<span class="user-type-badge">${profile.primaryUserType}</span>`
    : '';

  return `
    <div class="profile-card" id="profile-card" data-id="${profile.id}">
      <div class="swipe-indicator swipe-indicator-right" id="indicator-right">CONNECT</div>
      <div class="swipe-indicator swipe-indicator-left" id="indicator-left">PASS</div>
      <div class="profile-card-bg"></div>
      <div class="profile-card-top-badges">
        <span class="profile-tier-badge ${tier.cls}">${tier.label}</span>
        ${userTypeBadge}
      </div>
      <div class="profile-card-avatar">
        <div class="avatar avatar-lg" style="background:${bg};width:84px;height:84px;font-size:1.5rem;">
          ${avatarContent}
        </div>
      </div>
      <div class="profile-card-info">
        <div class="profile-card-name">${profile.displayName}</div>
        <div class="profile-card-role">${profile.headline}${profile.company ? ' @ ' + profile.company : ''}</div>
        ${locationLine}
        ${linkedInBadge}
        <div class="profile-card-tags">${tagsHtml}</div>
        ${profile.bio ? `<div class="profile-card-bio">${profile.bio}</div>` : ''}
        ${lookingForHtml ? `<div class="profile-card-looking-for">${lookingForHtml}</div>` : ''}
        <div class="profile-card-match">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
          ${profile.matchScore}% Match
        </div>
        ${profile.mutualConnections > 0 ? `<div class="profile-card-mutual">👥 ${profile.mutualConnections} mutual connection${profile.mutualConnections > 1 ? 's' : ''}</div>` : ''}
      </div>
    </div>
  `;
}

/* ── Swipe Interaction ── */

function attachSwipeListeners(outlet) {
  const area = outlet.querySelector('#swipe-area');
  const skipBtn = outlet.querySelector('#btn-skip');
  const connectBtn = outlet.querySelector('#btn-connect');
  const infoBtn = outlet.querySelector('#btn-info');

  // Retry button (on error state)
  const retryBtn = outlet.querySelector('#retry-btn');
  if (retryBtn) {
    retryBtn.addEventListener('click', () => renderConnect(outlet));
    return;
  }

  // Button actions
  skipBtn?.addEventListener('click', () => swipeOut('left', area, outlet));
  connectBtn?.addEventListener('click', () => swipeOut('right', area, outlet));
  infoBtn?.addEventListener('click', () => {
    const profile = profiles[currentIndex];
    if (profile) {
      const infoText = profile.bio || `${profile.displayName} — ${profile.headline}`;
      showToast(infoText.slice(0, 120) + (infoText.length > 120 ? '…' : ''), 'info');
    }
  });

  // Touch/mouse swipe
  setupDragListeners(area, outlet);
}

function setupDragListeners(area, outlet) {
  const card = area?.querySelector('#profile-card');
  if (!card) return;

  const onStart = (clientX) => {
    isDragging = true;
    startX = clientX;
    card.classList.add('swiping');
  };

  const onMove = (clientX) => {
    if (!isDragging) return;
    currentX = clientX - startX;
    const rotate = currentX * 0.08;
    card.style.transform = `translateX(${currentX}px) rotate(${rotate}deg)`;

    const rightInd = card.querySelector('#indicator-right');
    const leftInd = card.querySelector('#indicator-left');
    if (rightInd) rightInd.style.opacity = Math.min(currentX / 100, 1);
    if (leftInd) leftInd.style.opacity = Math.min(-currentX / 100, 1);
  };

  const onEnd = () => {
    if (!isDragging) return;
    isDragging = false;
    card.classList.remove('swiping');

    if (Math.abs(currentX) > 100) {
      swipeOut(currentX > 0 ? 'right' : 'left', area, outlet);
    } else {
      card.style.transform = '';
      const rightInd = card.querySelector('#indicator-right');
      const leftInd = card.querySelector('#indicator-left');
      if (rightInd) rightInd.style.opacity = 0;
      if (leftInd) leftInd.style.opacity = 0;
    }
    currentX = 0;
  };

  card.addEventListener('touchstart', (e) => onStart(e.touches[0].clientX), { passive: true });
  card.addEventListener('touchmove', (e) => onMove(e.touches[0].clientX), { passive: true });
  card.addEventListener('touchend', onEnd);

  card.addEventListener('mousedown', (e) => { e.preventDefault(); onStart(e.clientX); });
  document.addEventListener('mousemove', (e) => onMove(e.clientX));
  document.addEventListener('mouseup', onEnd);
}

function swipeOut(direction, area, outlet) {
  const card = area?.querySelector('#profile-card');
  if (!card) return;

  card.classList.add(direction === 'right' ? 'swipe-right' : 'swipe-left');

  const profile = profiles[currentIndex];

  // Record swipe in Firestore (fire-and-forget)
  if (profile) {
    recordSwipe(profile.id, direction).catch(err => {
      console.warn('[Cosmos] Failed to record swipe:', err);
    });
  }

  if (direction === 'right' && profile) {
    showToast(`Connected with ${profile.displayName}! 🎉`, 'success');
    const count = parseInt(localStorage.getItem('cosmos-connections-month') || '0') + 1;
    localStorage.setItem('cosmos-connections-month', count.toString());
  }

  setTimeout(() => {
    currentIndex++;
    const nextProfile = profiles[currentIndex] || null;
    area.innerHTML = renderProfileCard(nextProfile);

    // Update counter
    const counter = document.querySelector('#connect-counter');
    if (counter) {
      const left = 10 - parseInt(localStorage.getItem('cosmos-connections-month') || '0');
      counter.innerHTML = `<strong>${Math.max(0, left)}</strong> / 10 left`;
    }

    // Re-attach drag listeners for the new card
    if (nextProfile) {
      setupDragListeners(area, outlet);
    } else {
      // Hide action buttons when no more profiles
      const actions = document.querySelector('#swipe-actions');
      if (actions) actions.style.display = 'none';
    }
  }, 500);
}
