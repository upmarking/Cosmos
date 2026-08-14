/* ============================================================
   Cosmos PWA — Search Page
   Real-time search across profiles, interest tags, and roles
   ============================================================ */

import {
  auth, db, collection, getDocs, doc, getDoc, addDoc, setDoc, updateDoc,
  serverTimestamp, onSnapshot, increment, query, where
} from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

/* ── State ── */
let allProfiles = [];
let searchQuery = '';
let activeFilter = 'All';
let currentUserProfile = null;

// Real-time status cache
let connectionsMap = new Map();       // otherUserId -> connectionId
let incomingRequestsMap = new Map();  // senderId -> requestId
let outgoingRequestsMap = new Map();  // receiverId -> requestId

// Listeners
let unsubConnections = null;
let unsubIncoming = null;
let unsubOutgoing = null;

/**
 * Fetches all other profiles from Firestore
 */
async function loadAllProfiles(uid) {
  try {
    const snap = await getDocs(collection(db, 'users'));
    const list = [];
    snap.forEach((d) => {
      const data = d.data();
      if (d.id === uid) return;
      if (data.isRestricted === true) return;
      if (d.id.startsWith('mock_user_')) return;

      const initials = (data.name || '')
        .split(' ')
        .map((w) => w.charAt(0))
        .join('')
        .toUpperCase()
        .slice(0, 2) || 'U';

      list.push({
        id: d.id,
        name: data.name || 'Unknown Builder',
        headline: data.headline || data.role || 'Cosmos Builder',
        company: data.company || '',
        avatarUrl: data.avatarUrl || '',
        location: data.location || '',
        tags: data.tags || [],
        bio: data.bio || '',
        primaryUserType: data.primaryUserType || '',
        isLinkedInConnected: data.isLinkedInConnected || false,
        initials
      });
    });
    allProfiles = list;
  } catch (err) {
    console.error('[Cosmos Search] Failed to fetch users:', err);
    throw err;
  }
}

/**
 * Set up real-time connection state listeners for the active user
 */
function startConnectionListeners(uid, onUpdate) {
  stopConnectionListeners();

  // 1. Listen to active connections
  const connQuery = query(collection(db, 'connections'), where('members', 'array-contains', uid));
  unsubConnections = onSnapshot(connQuery, (snapshot) => {
    connectionsMap.clear();
    snapshot.forEach((d) => {
      const data = d.data();
      if (data.status === 'ACTIVE') {
        const otherId = data.members.find((m) => m !== uid);
        if (otherId) connectionsMap.set(otherId, d.id);
      }
    });
    onUpdate();
  }, (err) => console.warn('[Cosmos Search] Connections listener error:', err));

  // 2. Listen to incoming pending connection requests
  const incomingQuery = query(
    collection(db, 'connection_requests'),
    where('receiverId', '==', uid),
    where('status', '==', 'PENDING')
  );
  unsubIncoming = onSnapshot(incomingQuery, (snapshot) => {
    incomingRequestsMap.clear();
    snapshot.forEach((d) => {
      const data = d.data();
      incomingRequestsMap.set(data.senderId, d.id);
    });
    onUpdate();
  }, (err) => console.warn('[Cosmos Search] Incoming requests listener error:', err));

  // 3. Listen to outgoing pending connection requests
  const outgoingQuery = query(
    collection(db, 'connection_requests'),
    where('senderId', '==', uid),
    where('status', '==', 'PENDING')
  );
  unsubOutgoing = onSnapshot(outgoingQuery, (snapshot) => {
    outgoingRequestsMap.clear();
    snapshot.forEach((d) => {
      const data = d.data();
      outgoingRequestsMap.set(data.receiverId, d.id);
    });
    onUpdate();
  }, (err) => console.warn('[Cosmos Search] Outgoing requests listener error:', err));
}

function stopConnectionListeners() {
  if (unsubConnections) { unsubConnections(); unsubConnections = null; }
  if (unsubIncoming) { unsubIncoming(); unsubIncoming = null; }
  if (unsubOutgoing) { unsubOutgoing(); unsubOutgoing = null; }
  connectionsMap.clear();
  incomingRequestsMap.clear();
  outgoingRequestsMap.clear();
}

/* ── Rendering ── */

export async function renderSearch(outlet) {
  const user = auth.currentUser;
  if (!user) {
    router.navigate('/auth');
    return;
  }

  // Set default view structure
  outlet.innerHTML = `
    <div class="search-page page">
      <div class="page-header" style="margin-bottom: 1.5rem;">
        <div>
          <h1 class="page-title">Search Builders</h1>
          <p class="page-subtitle">Find and connect with founders, operators, and investors</p>
        </div>
      </div>

      <!-- Glassmorphic Search Bar -->
      <div class="search-wrap" style="margin-bottom: 1.25rem;">
        <svg class="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <circle cx="11" cy="11" r="8"></circle>
          <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
        <input class="search-input" type="text" id="search-bar" placeholder="Search by name, role, company, bio or tag..." style="padding-left: 2.75rem;" />
      </div>

      <!-- Filter Pills -->
      <div class="filter-pills-wrap" style="display: flex; gap: 0.5rem; overflow-x: auto; padding-bottom: 0.5rem; margin-bottom: 1.5rem; scrollbar-width: none;">
        <button class="filter-pill active" data-filter="All">All</button>
        <button class="filter-pill" data-filter="Founder">Founders</button>
        <button class="filter-pill" data-filter="Operator">Operators</button>
        <button class="filter-pill" data-filter="Investor">Investors</button>
        <button class="filter-pill" data-filter="Mentor">Mentors</button>
        <button class="filter-pill" data-filter="Student">Students</button>
      </div>

      <!-- Results Grid -->
      <div class="search-results-grid" id="search-results" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem;">
        <div class="loading-spinner" style="grid-column: 1 / -1;"></div>
      </div>
    </div>
  `;

  const searchResults = outlet.querySelector('#search-results');
  const searchBar = outlet.querySelector('#search-bar');

  // Trigger search result update
  const triggerUpdate = () => {
    renderFilteredResults(searchResults, user.uid);
  };

  try {
    // Parallel load profile info & user database
    const [meSnap] = await Promise.all([
      getDoc(doc(db, 'users', user.uid)),
      loadAllProfiles(user.uid)
    ]);
    currentUserProfile = meSnap.exists() ? meSnap.data() : {};

    // Start snapshot listeners for live connection states
    startConnectionListeners(user.uid, triggerUpdate);

    // Initial render
    triggerUpdate();

    // Attach search query listener
    searchBar.addEventListener('input', (e) => {
      searchQuery = e.target.value.toLowerCase().trim();
      triggerUpdate();
    });

    // Attach filter pill listeners
    outlet.querySelectorAll('.filter-pill').forEach((pill) => {
      pill.addEventListener('click', (e) => {
        outlet.querySelectorAll('.filter-pill').forEach((p) => p.classList.remove('active'));
        e.target.classList.add('active');
        activeFilter = e.target.dataset.filter;
        triggerUpdate();
      });
    });

  } catch (err) {
    searchResults.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1;">
        <div class="empty-state-icon">⚠️</div>
        <h3 class="empty-state-title">Failed to load builders</h3>
        <p class="empty-state-desc">${err.message || 'Check your internet connection.'}</p>
      </div>
    `;
  }

  // Setup action handlers on window
  window.handleSearchConnect = async (targetId) => {
    const target = allProfiles.find((p) => p.id === targetId);
    if (!target) return;

    try {
      const requestId = `req_${user.uid}_${targetId}`;
      await setDoc(doc(db, 'connection_requests', requestId), {
        senderId: user.uid,
        receiverId: targetId,
        senderName: currentUserProfile?.name || window.cosmosApp?.userProfile?.name || 'Builder',
        senderHeadline: currentUserProfile?.headline || window.cosmosApp?.userProfile?.headline || '',
        senderAvatarUrl: currentUserProfile?.avatarUrl || window.cosmosApp?.userProfile?.avatarUrl || '',
        receiverName: target.name || '',
        receiverHeadline: target.headline || '',
        receiverAvatarUrl: target.avatarUrl || '',
        message: '',
        status: 'PENDING',
        createdAt: serverTimestamp()
      });

      // Notification
      await addDoc(collection(db, 'notifications'), {
        userId: targetId,
        type: 'CONNECTION_REQUEST',
        title: 'New Connection Request',
        body: `${currentUserProfile?.name || window.cosmosApp?.userProfile?.name || 'Builder'} wants to connect with you.`,
        timestamp: serverTimestamp(),
        isRead: false,
        actionId: user.uid
      });

      showToast(`Request sent to ${target.name}`, 'success');
    } catch (e) {
      console.error(e);
      showToast('Failed to send connection request', 'error');
    }
  };

  window.handleSearchAccept = async (requestId, senderId, senderName) => {
    try {
      await updateDoc(doc(db, 'connection_requests', requestId), { status: 'ACCEPTED' });

      const connectionId = user.uid < senderId ? `${user.uid}_${senderId}` : `${senderId}_${user.uid}`;
      await setDoc(doc(db, 'connections', connectionId), {
        id: connectionId,
        members: [user.uid, senderId],
        lastMessage: 'Connection established! Say hello.',
        lastMessageTime: serverTimestamp(),
        unreadCountMap: { [user.uid]: 0, [senderId]: 0 },
        labels: { [user.uid]: [], [senderId]: [] },
        privateGoals: { [user.uid]: '', [senderId]: '' },
        status: 'ACTIVE',
        createdAt: serverTimestamp()
      });

      await updateDoc(doc(db, 'users', user.uid), {
        connectionsCount: increment(1),
        followersCount: increment(1),
        followingCount: increment(1)
      });
      await updateDoc(doc(db, 'users', senderId), {
        connectionsCount: increment(1),
        followersCount: increment(1),
        followingCount: increment(1)
      });

      await addDoc(collection(db, 'notifications'), {
        userId: senderId,
        type: 'CONNECTION_ACCEPTED',
        title: 'Connection Accepted! 🎉',
        body: 'Your connection request was accepted. Start a conversation now!',
        timestamp: serverTimestamp(),
        isRead: false,
        actionId: user.uid
      });
      await addDoc(collection(db, 'notifications'), {
        userId: user.uid,
        type: 'CONNECTION_ACCEPTED',
        title: 'Connection Established! 🎉',
        body: `You are now connected with ${senderName}. Start a conversation!`,
        timestamp: serverTimestamp(),
        isRead: false,
        actionId: senderId
      });

      showToast(`Connected with ${senderName}! 🎉`, 'success');
    } catch (e) {
      console.error(e);
      showToast('Failed to accept request', 'error');
    }
  };

  window.handleSearchDecline = async (requestId) => {
    try {
      await updateDoc(doc(db, 'connection_requests', requestId), { status: 'DECLINED' });
      showToast('Request declined', 'info');
    } catch (e) {
      console.error(e);
      showToast('Failed to decline request', 'error');
    }
  };

  window.handleSearchWithdraw = async (requestId) => {
    try {
      await updateDoc(doc(db, 'connection_requests', requestId), { status: 'WITHDRAWN' });
      showToast('Request withdrawn', 'info');
    } catch (e) {
      console.error(e);
      showToast('Failed to withdraw request', 'error');
    }
  };

  // Cleanup on route departure
  return () => {
    stopConnectionListeners();
    delete window.handleSearchConnect;
    delete window.handleSearchAccept;
    delete window.handleSearchDecline;
    delete window.handleSearchWithdraw;
  };
}

/**
 * Filter list and render in the results element
 */
function renderFilteredResults(container, myUid) {
  const filtered = allProfiles.filter((p) => {
    // 1. Filter query
    const matchesQuery = searchQuery.length === 0 ||
      p.name.toLowerCase().includes(searchQuery) ||
      p.headline.toLowerCase().includes(searchQuery) ||
      p.company.toLowerCase().includes(searchQuery) ||
      p.bio.toLowerCase().includes(searchQuery) ||
      p.tags.some(t => t.toLowerCase().includes(searchQuery));

    // 2. Filter User Type pill
    const matchesType = activeFilter === 'All' ||
      p.primaryUserType.toLowerCase() === activeFilter.toLowerCase();

    return matchesQuery && matchesType;
  });

  if (filtered.length === 0) {
    container.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1; margin: 2rem auto; width: 100%;">
        <div class="empty-state-icon">🔍</div>
        <h3 class="empty-state-title">No builders found</h3>
        <p class="empty-state-desc">Try modifying your search or filter pills.</p>
      </div>
    `;
    return;
  }

  const badgeColors = {
    founder: 'badge-amber',
    investor: 'badge-green',
    operator: 'badge-blue',
    mentor: 'badge-purple',
    student: 'badge-blue'
  };

  container.innerHTML = filtered.map((item) => {
    // Determine connection state
    let buttonHtml = '';
    const isConn = connectionsMap.has(item.id);
    const incomingReqId = incomingRequestsMap.get(item.id);
    const outgoingReqId = outgoingRequestsMap.get(item.id);

    if (isConn) {
      buttonHtml = `
        <button class="btn btn-secondary btn-sm" onclick="window.location.hash='#/messenger'" style="border-radius:18px;padding:0.4rem 1rem;font-size:0.75rem;display:flex;align-items:center;gap:4px;">
          💬 Chat
        </button>
      `;
    } else if (incomingReqId) {
      buttonHtml = `
        <div style="display:flex;gap:4px;">
          <button class="btn btn-primary btn-sm" onclick="handleSearchAccept('${incomingReqId}', '${item.id}', '${item.name.replace(/'/g, "\\'")}')" style="border-radius:18px;padding:0.35rem 0.75rem;font-size:0.72rem;">Accept</button>
          <button class="btn btn-outline-danger btn-sm" onclick="handleSearchDecline('${incomingReqId}')" style="border-radius:18px;padding:0.35rem 0.75rem;font-size:0.72rem;">Decline</button>
        </div>
      `;
    } else if (outgoingReqId) {
      buttonHtml = `
        <button class="btn btn-outline btn-sm" onclick="handleSearchWithdraw('${outgoingReqId}')" style="border-radius:18px;padding:0.4rem 1rem;font-size:0.75rem;color:var(--text-muted);border-color:var(--border);">
          Pending
        </button>
      `;
    } else {
      buttonHtml = `
        <button class="btn btn-primary btn-sm" onclick="handleSearchConnect('${item.id}')" style="border-radius:18px;padding:0.4rem 1rem;font-size:0.75rem;display:flex;align-items:center;gap:4px;">
          ⚡ Connect
        </button>
      `;
    }

    const typeBadge = item.primaryUserType
      ? `<span class="badge ${badgeColors[item.primaryUserType.toLowerCase()] || 'badge-purple'}">${item.primaryUserType}</span>`
      : '';

    const tagsHtml = item.tags.slice(0, 3).map((t, idx) => {
      const cls = ['tag-blue', 'tag-pink', 'tag-green', 'tag-amber'][idx % 4];
      return `<span class="tag ${cls}" style="font-size:0.65rem;padding:2px 6px;border-radius:4px;">${t}</span>`;
    }).join('');

    const username = '@' + item.name.toLowerCase().replace(/ /g, '');
    const avatarHtml = item.avatarUrl
      ? `<img src="${item.avatarUrl}" alt="${item.name}" style="width:100%;height:100%;object-fit:cover;border-radius:50%;" />`
      : item.initials;

    return `
      <div class="card card-glass" style="display:flex;flex-direction:column;justify-content:space-between;padding:1.25rem;border-radius:20px;height:240px;position:relative;">
        <div style="display:flex;align-items:flex-start;gap:0.75rem;">
          <div class="avatar avatar-md" style="width:48px;height:48px;border-radius:50%;flex-shrink:0;${item.avatarUrl ? '' : 'background:var(--gradient-primary);'}">
            ${avatarHtml}
          </div>
          <div style="flex:1;min-width:0;">
            <div style="display:flex;align-items:center;justify-content:space-between;gap:4px;">
              <span style="font-weight:800;font-size:0.95rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${item.name}</span>
              ${typeBadge}
            </div>
            <div style="font-size:0.75rem;color:var(--text-muted);margin-bottom:0.25rem;">${username}</div>
            <div style="font-size:0.8rem;font-weight:500;color:var(--purple-l);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${item.headline}</div>
            ${item.company ? `<div style="font-size:0.75rem;color:var(--text-secondary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">🏢 ${item.company}</div>` : ''}
            ${item.location ? `<div style="font-size:0.72rem;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px;">📍 ${item.location}</div>` : ''}
          </div>
        </div>

        <div style="margin-top:0.75rem;flex:1;display:flex;flex-direction:column;justify-content:flex-end;">
          <div style="display:flex;gap:3px;flex-wrap:wrap;margin-bottom:0.75rem;">
            ${tagsHtml}
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;">
            ${item.isLinkedInConnected ? `
              <span style="font-size:0.7rem;color:var(--blue);font-weight:600;display:inline-flex;align-items:center;gap:3px;">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.779-1.75-1.75s.784-1.75 1.75-1.75 1.75.779 1.75 1.75-.784 1.75-1.75 1.75zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z"/></svg>
                Verified
              </span>
            ` : '<span></span>'}
            ${buttonHtml}
          </div>
        </div>
      </div>
    `;
  }).join('');
}
