/* ============================================================
   Cosmos PWA — Orbits Page (Communities)
   Maps to the second 'O' in COSMOS navigation
   ============================================================ */

import {
  auth, db, collection, query, onSnapshot, doc, getDoc, setDoc, updateDoc,
  addDoc, arrayUnion, arrayRemove, deleteDoc, increment, serverTimestamp
} from '../firebase-config.js';
import { showToast } from '../app.js';

let unsubOrbit = null;
let unsubMembers = null;
let unsubPosts = null;
const userCache = new Map();

function formatTime(timestamp) {
  if (!timestamp) return 'now';
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
  const diff = Date.now() - date.getTime();
  if (diff < 60000) return 'now';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
  return date.toLocaleDateString([], { day: 'numeric', month: 'short' });
}

async function getOrFetchUserProfile(uid) {
  if (!uid) return null;
  if (userCache.has(uid)) return userCache.get(uid);

  try {
    const snap = await getDoc(doc(db, 'users', uid));
    if (snap.exists()) {
      userCache.set(uid, snap.data());
      return snap.data();
    }
  } catch (e) {
    console.error('[Cosmos Orbits] Error fetching user profile for cache:', e);
  }
  return null;
}

function cleanupOrbitDetail() {
  if (unsubOrbit) {
    unsubOrbit();
    unsubOrbit = null;
  }
  if (unsubMembers) {
    unsubMembers();
    unsubMembers = null;
  }
  if (unsubPosts) {
    unsubPosts();
    unsubPosts = null;
  }
}

export async function renderOrbits(outlet, path) {
  const user = auth.currentUser;
  if (!user) return;

  // Route check: detail view subpath
  const parts = path ? path.split('/').filter(Boolean) : [];
  if (parts.length > 1 && parts[1] === 'detail') {
    const orbitId = parts[2];
    renderOrbitDetail(outlet, orbitId);
    return () => cleanupOrbitDetail();
  }

  // Otherwise clean up detail listeners and render list
  cleanupOrbitDetail();

  // Cleanup list listeners
  if (window.cosmosApp._circlesUnsubscribe) {
    window.cosmosApp._circlesUnsubscribe();
    window.cosmosApp._circlesUnsubscribe = null;
  }
  if (window.cosmosApp._userCirclesUnsubscribe) {
    window.cosmosApp._userCirclesUnsubscribe();
    window.cosmosApp._userCirclesUnsubscribe = null;
  }

  outlet.innerHTML = `
    <div class="communities-page page">
      <div class="page-header" style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:0.75rem;">
        <div>
          <h1 class="page-title">Orbits</h1>
          <p class="page-subtitle">Curated communities for like-minded builders</p>
        </div>
        <button class="btn btn-primary btn-sm" id="btn-create-orbit" style="display:flex; align-items:center; gap:0.25rem;">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Create Orbit
        </button>
      </div>
      <div class="search-wrap" style="margin-bottom:1.25rem;">
        <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        <input class="search-input" type="text" id="orbit-search" placeholder="Search orbits..." />
      </div>
      <div id="orbits-list" class="stagger">
        <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
      </div>
    </div>

    <!-- CREATE ORBIT MODAL -->
    <div class="modal-overlay hidden" id="create-orbit-modal">
      <div class="modal-card">
        <div class="modal-header">
          <h3>Create New Orbit</h3>
          <button class="modal-close" id="btn-close-orbit-modal" aria-label="Close modal">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <form id="create-orbit-form">
            <div class="form-group">
              <label class="form-label" for="orbit-name">Orbit Name</label>
              <input class="form-input" type="text" id="orbit-name" required placeholder="e.g. AI Builders & Founders" />
            </div>
            
            <div class="form-group">
              <label class="form-label" for="orbit-desc">Description</label>
              <textarea class="form-input" id="orbit-desc" required placeholder="Describe the purpose of this community..." style="min-height:90px;"></textarea>
            </div>
            
            <div class="form-group">
              <label class="form-label" for="orbit-theme">Theme / Category</label>
              <input class="form-input" type="text" id="orbit-theme" required placeholder="e.g. AI, Design, Web3" />
            </div>

            <div class="form-group">
              <label class="form-label" for="orbit-tags">Tags (comma-separated)</label>
              <input class="form-input" type="text" id="orbit-tags" placeholder="e.g. LLMs, Founders, Coding" />
            </div>

            <div class="switch-group" style="margin-top:1rem;">
              <div class="switch-label-wrap">
                <span class="switch-title">Make Private</span>
                <span class="switch-description">Only approved members can view posts and members</span>
              </div>
              <label class="switch">
                <input type="checkbox" id="orbit-is-private" />
                <span class="slider"></span>
              </label>
            </div>
          </form>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary btn-sm" id="btn-cancel-orbit" type="button">Cancel</button>
          <button class="btn btn-primary btn-sm" id="btn-submit-orbit" type="submit" form="create-orbit-form">Create Orbit</button>
        </div>
      </div>
    </div>
  `;

  const modal = outlet.querySelector('#create-orbit-modal');
  const btnCreate = outlet.querySelector('#btn-create-orbit');
  const btnClose = outlet.querySelector('#btn-close-orbit-modal');
  const btnCancel = outlet.querySelector('#btn-cancel-orbit');
  const createForm = outlet.querySelector('#create-orbit-form');
  const btnSubmit = outlet.querySelector('#btn-submit-orbit');

  const openModal = () => {
    modal.classList.remove('hidden');
    document.body.style.overflow = 'hidden';
  };

  const closeModal = () => {
    modal.classList.add('hidden');
    document.body.style.overflow = '';
    createForm.reset();
  };

  btnCreate.addEventListener('click', openModal);
  btnClose.addEventListener('click', closeModal);
  btnCancel.addEventListener('click', closeModal);

  createForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const name = outlet.querySelector('#orbit-name').value.trim();
    const description = outlet.querySelector('#orbit-desc').value.trim();
    const theme = outlet.querySelector('#orbit-theme').value.trim();
    const isPrivate = outlet.querySelector('#orbit-is-private').checked;
    const tagsInput = outlet.querySelector('#orbit-tags').value;
    const tags = tagsInput ? tagsInput.split(',').map(t => t.trim()).filter(Boolean) : [theme || 'Community'];

    if (!name || !description || !theme) {
      showToast('Please fill in all required fields.', 'error');
      return;
    }

    btnSubmit.disabled = true;
    btnSubmit.textContent = 'Creating...';

    try {
      const adminName = (window.cosmosApp.userProfile && window.cosmosApp.userProfile.name) || user.displayName || user.email.split('@')[0] || 'Admin';

      const circleData = {
        name,
        description,
        theme,
        tags,
        isPrivate,
        coverUrl: '',
        memberCount: 1,
        adminName,
        createdBy: user.uid,
        createdAt: serverTimestamp()
      };

      const circleRef = await addDoc(collection(db, 'circles'), circleData);
      const newCircleId = circleRef.id;

      const memberRef = doc(db, 'circles', newCircleId, 'members', user.uid);
      await setDoc(memberRef, { joinedAt: serverTimestamp(), status: 'APPROVED' });

      const userRef = doc(db, 'users', user.uid);
      await updateDoc(userRef, { joinedCircles: arrayUnion(newCircleId) });

      showToast(`Orbit "${name}" created successfully! 🚀`, 'success');
      closeModal();
    } catch (err) {
      console.error('[Cosmos Orbits] Create orbit error:', err);
      showToast('Failed to create orbit: ' + err.message, 'error');
    } finally {
      btnSubmit.disabled = false;
      btnSubmit.textContent = 'Create Orbit';
    }
  });

  let joinedCircles = [];
  let pendingCircles = [];
  let circlesList = [];

  const renderList = () => {
    const container = outlet.querySelector('#orbits-list');
    if (!container) return;

    const list = circlesList.map(orbit => {
      const isJoined = joinedCircles.includes(orbit.id) || orbit.createdBy === user.uid;
      const isPending = pendingCircles.includes(orbit.id);
      return { ...orbit, isJoined, isPending };
    });

    const searchInput = outlet.querySelector('#orbit-search');
    const q = searchInput ? searchInput.value.toLowerCase() : '';
    const filtered = list.filter(o =>
      o.name.toLowerCase().includes(q) || o.description.toLowerCase().includes(q) || o.tags.some(t => t.toLowerCase().includes(q))
    );

    container.innerHTML = renderOrbitCards(filtered);
    attachOrbitListeners(outlet, filtered, user.uid);
  };

  const userRef = doc(db, 'users', user.uid);
  window.cosmosApp._userCirclesUnsubscribe = onSnapshot(userRef, (userSnap) => {
    if (userSnap.exists()) {
      const uData = userSnap.data();
      joinedCircles = uData.joinedCircles || [];
      pendingCircles = uData.pendingCircles || [];
    }

    if (circlesList.length > 0) {
      renderList();
    }
  });

  window.cosmosApp._circlesUnsubscribe = onSnapshot(collection(db, 'circles'), (snapshot) => {
    circlesList = [];
    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      if (data.createdBy && data.createdBy.startsWith('mock_user_')) return;

      circlesList.push({
        id: docSnap.id,
        name: data.name || 'Unnamed Orbit',
        description: data.description || '',
        coverUrl: data.coverUrl || '',
        memberCount: data.memberCount || 0,
        theme: data.theme || '',
        tags: data.tags || [],
        isPrivate: data.isPrivate || false,
        adminName: data.adminName || 'Admin',
        createdBy: data.createdBy || '',
      });
    });

    renderList();
  }, (error) => {
    console.error('[Cosmos Orbits] Circles snapshot error:', error);
    const container = outlet.querySelector('#orbits-list');
    if (container) {
      container.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load circles: ${error.message}</div>`;
    }
  });

  setTimeout(() => {
    const searchInput = outlet.querySelector('#orbit-search');
    if (searchInput) {
      searchInput.addEventListener('input', () => {
        renderList();
      });
    }
  }, 100);

  return () => {
    if (window.cosmosApp._circlesUnsubscribe) {
      window.cosmosApp._circlesUnsubscribe();
      window.cosmosApp._circlesUnsubscribe = null;
    }
    if (window.cosmosApp._userCirclesUnsubscribe) {
      window.cosmosApp._userCirclesUnsubscribe();
      window.cosmosApp._userCirclesUnsubscribe = null;
    }
    cleanupOrbitDetail();
  };
}

function renderOrbitCards(orbits) {
  if (!orbits.length) {
    return `
      <div class="empty-state">
        <div class="empty-state-icon">🌐</div>
        <h3 class="empty-state-title">No Orbits Found</h3>
        <p class="empty-state-desc">Try a different search term to find your community.</p>
      </div>
    `;
  }

  return orbits.map((orbit, i) => {
    const initials = orbit.name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) || 'O';
    const tagColors = ['tag-purple', 'tag-blue', 'tag-pink', 'tag-amber', 'tag-green'];

    const buttonText = orbit.isJoined ? 'View Orbit' : (orbit.isPending ? 'Requested' : 'Join Orbit');
    const buttonClass = orbit.isJoined ? 'btn-secondary' : 'btn-primary';

    return `
      <div class="orbit-card anim-fade-up" data-id="${orbit.id}" style="animation-delay:${i * 0.06}s;">
        <div class="orbit-card-header">
          <div class="orbit-icon" style="background:var(--gradient-primary);color:var(--bg-primary);font-weight:bold;display:flex;align-items:center;justify-content:center;">
            ${orbit.coverUrl ? `<img src="${orbit.coverUrl}" style="width:100%;height:100%;object-fit:cover;border-radius:12px;" />` : initials}
          </div>
          <div>
            <div class="orbit-card-name">${orbit.name}</div>
            <div class="orbit-card-members">${orbit.memberCount} members · ${orbit.isPrivate ? 'Private' : 'Public'}</div>
          </div>
        </div>
        <p class="orbit-card-desc">${orbit.description}</p>
        <div class="event-card-tags" style="margin-bottom:0.75rem;">
          ${orbit.tags.map((t, idx) => `<span class="tag ${tagColors[idx % tagColors.length]}">${t}</span>`).join('')}
        </div>
        <div class="orbit-card-footer">
          <div style="display:flex;gap:0.35rem;">
            ${orbit.isJoined ? '<span class="badge badge-green">✓ Member</span>' : ''}
            ${orbit.isPending ? '<span class="badge badge-amber">🕒 Pending</span>' : ''}
          </div>
          <button class="btn ${buttonClass} btn-sm orbit-join-btn" data-orbit-id="${orbit.id}" ${orbit.isPending ? 'disabled' : ''}>
            ${buttonText}
          </button>
        </div>
      </div>
    `;
  }).join('');
}

function attachOrbitListeners(outlet, orbits, currentUserId) {
  outlet.querySelectorAll('.orbit-join-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const orbitId = btn.dataset.orbitId;
      const orbit = orbits.find(o => o.id === orbitId);
      if (!orbit) return;

      if (orbit.isJoined) {
        window.location.hash = `#/orbits/detail/${orbitId}`;
        return;
      }

      btn.disabled = true;
      btn.textContent = 'Joining...';

      try {
        const circleRef = doc(db, 'circles', orbitId);
        const userRef = doc(db, 'users', currentUserId);
        const memberRef = doc(db, 'circles', orbitId, 'members', currentUserId);

        const circleSnap = await getDoc(circleRef);
        if (!circleSnap.exists()) {
          showToast('Orbit not found', 'error');
          btn.disabled = false;
          btn.textContent = orbit.isPrivate ? 'Join Orbit' : 'Join Orbit';
          return;
        }

        const isPrivate = orbit.isPrivate;
        if (isPrivate) {
          await setDoc(memberRef, { joinedAt: serverTimestamp(), status: 'PENDING' });
          await updateDoc(userRef, { pendingCircles: arrayUnion(orbitId) });

          if (orbit.createdBy) {
            const userSnap = await getDoc(userRef);
            const senderName = (userSnap.exists() && userSnap.data().name) || 'A builder';
            await addDoc(collection(db, 'notifications'), {
              userId: orbit.createdBy,
              type: 'COMMUNITY_ANNOUNCEMENT',
              title: 'New Orbit Join Request! 🚀',
              body: `${senderName} wants to join your orbit "${orbit.name}".`,
              timestamp: serverTimestamp(),
              isRead: false,
              actionId: orbitId
            });
          }
          
          btn.textContent = 'Requested';
          btn.classList.remove('btn-primary');
          btn.classList.add('btn-secondary');
          showToast(`Request sent to join "${orbit.name}"! 🚀`, 'success');
        } else {
          await setDoc(memberRef, { joinedAt: serverTimestamp(), status: 'APPROVED' });
          await updateDoc(circleRef, { memberCount: increment(1) });
          await updateDoc(userRef, { joinedCircles: arrayUnion(orbitId) });
          
          btn.textContent = 'View Orbit';
          btn.classList.remove('btn-primary');
          btn.classList.add('btn-secondary');
          showToast(`Joined "${orbit.name}"! 🎉`, 'success');
        }
      } catch (err) {
        console.error('[Cosmos Orbits] Join error:', err);
        showToast('Failed to join orbit', 'error');
        btn.disabled = false;
        btn.textContent = orbit.isPrivate ? 'Join Orbit' : 'Join Orbit';
      }
    });
  });

  outlet.querySelectorAll('.orbit-card').forEach(card => {
    card.addEventListener('click', () => {
      const orbitId = card.dataset.id;
      const orbit = orbits.find(o => o.id === orbitId);
      if (orbit) {
        if (orbit.isJoined) {
          window.location.hash = `#/orbits/detail/${orbitId}`;
        } else {
          showToast(orbit.isPrivate ? `"${orbit.name}" is private. Request access to join.` : `Join "${orbit.name}" to view discussions.`, 'info');
        }
      }
    });
  });
}

async function renderOrbitDetail(outlet, orbitId) {
  const user = auth.currentUser;
  if (!user) return;

  cleanupOrbitDetail();

  // Show loading spinner
  outlet.innerHTML = `
    <div class="orbit-detail-page page">
      <div class="loading-spinner" style="margin:5rem auto; display:block;"></div>
    </div>
  `;

  unsubOrbit = onSnapshot(doc(db, 'circles', orbitId), async (docSnap) => {
    if (!docSnap.exists()) {
      outlet.innerHTML = `
        <div class="orbit-detail-page page">
          <div style="text-align:center;padding:3rem 1rem;">
            <div style="font-size:2.5rem;margin-bottom:1rem;">🌐</div>
            <h3>Orbit Not Found</h3>
            <p style="color:var(--text-muted);margin:0.5rem 0 1.5rem;">This community may have been deleted by its manager.</p>
            <button class="btn btn-primary btn-sm" onclick="window.location.hash='#/orbits'">Back to Orbits</button>
          </div>
        </div>
      `;
      return;
    }

    const orbit = { id: docSnap.id, ...docSnap.data() };
    const contentDiv = outlet.querySelector('.orbit-detail-content');
    
    if (!contentDiv) {
      renderOrbitDetailSkeleton(outlet, orbit);
    } else {
      updateOrbitHeader(outlet, orbit);
    }
  }, (err) => {
    console.error('[Cosmos Orbits] Detail snapshot error:', err);
    showToast('Failed to load Orbit details', 'error');
  });
}

function renderOrbitDetailSkeleton(outlet, orbit) {
  const user = auth.currentUser;
  const isManager = orbit.createdBy === user.uid;
  const initials = orbit.name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) || 'O';

  outlet.innerHTML = `
    <div class="orbit-detail-page page orbit-detail-content">
      <div class="orbit-detail-hero">
        <div class="orbit-detail-hero-bg"></div>
        <button class="orbit-detail-back-btn" id="btn-back-to-orbits" aria-label="Go back">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        
        <div class="orbit-detail-avatar-wrap">
          <div class="orbit-icon" style="width:72px;height:72px;background:var(--gradient-primary);color:var(--bg-primary);font-size:1.8rem;font-weight:bold;margin:0 auto;display:flex;align-items:center;justify-content:center;border-radius:18px;box-shadow:0 8px 24px rgba(167,139,250,0.25);">
            ${orbit.coverUrl ? `<img src="${orbit.coverUrl}" style="width:100%;height:100%;object-fit:cover;border-radius:18px;" />` : initials}
          </div>
        </div>

        <h1 class="orbit-detail-name" id="orbit-header-name">${escapeHTML(orbit.name)}</h1>
        
        <div class="orbit-detail-meta" id="orbit-header-meta">
          <span class="badge badge-purple" id="orbit-header-theme">${escapeHTML(orbit.theme || 'Community')}</span>
          <span>·</span>
          <span id="orbit-header-count">${orbit.memberCount || 0} members</span>
          <span>·</span>
          <span id="orbit-header-privacy">${orbit.isPrivate ? 'Private Orbit 🔒' : 'Public Orbit 🔓'}</span>
        </div>

        <p class="orbit-detail-desc" id="orbit-header-desc">${escapeHTML(orbit.description)}</p>
        
        <div style="margin-top:1.25rem; display:flex; justify-content:center; gap:0.5rem;" id="orbit-header-actions">
        </div>
      </div>

      <div class="orbit-tabs" id="orbit-detail-tabs">
        <button class="orbit-tab active" data-tab="feed">Feed</button>
        <button class="orbit-tab" data-tab="members">Members</button>
        ${isManager ? `<button class="orbit-tab" data-tab="manage">Manage</button>` : ''}
      </div>

      <div class="orbit-tab-content" id="orbit-detail-tab-content">
      </div>
    </div>
  `;

  function escapeHTML(str) {
    if (!str) return '';
    return str.replace(/[&<>'"]/g, 
      tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)
    );
  }

  outlet.querySelector('#btn-back-to-orbits').addEventListener('click', () => {
    window.location.hash = '#/orbits';
  });

  updateOrbitActions(outlet, orbit);

  let activeTab = 'feed';
  const tabs = outlet.querySelectorAll('.orbit-tab');
  
  const switchTab = (tabName) => {
    activeTab = tabName;
    tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === tabName));
    renderActiveTabContent(outlet, orbit, activeTab);
  };

  tabs.forEach(t => {
    t.addEventListener('click', () => switchTab(t.dataset.tab));
  });

  renderActiveTabContent(outlet, orbit, activeTab);
}

function updateOrbitHeader(outlet, orbit) {
  const nameEl = outlet.querySelector('#orbit-header-name');
  const themeEl = outlet.querySelector('#orbit-header-theme');
  const countEl = outlet.querySelector('#orbit-header-count');
  const privacyEl = outlet.querySelector('#orbit-header-privacy');
  const descEl = outlet.querySelector('#orbit-header-desc');

  if (nameEl) nameEl.textContent = orbit.name;
  if (themeEl) themeEl.textContent = orbit.theme || 'Community';
  if (countEl) countEl.textContent = `${orbit.memberCount || 0} members`;
  if (privacyEl) privacyEl.textContent = orbit.isPrivate ? 'Private Orbit 🔒' : 'Public Orbit 🔓';
  if (descEl) descEl.textContent = orbit.description;

  updateOrbitActions(outlet, orbit);
}

function updateOrbitActions(outlet, orbit) {
  const container = outlet.querySelector('#orbit-header-actions');
  if (!container) return;

  const user = auth.currentUser;
  if (orbit.createdBy === user.uid) {
    container.innerHTML = `<span class="badge badge-purple" style="padding:0.4rem 0.8rem; font-weight:bold;">👑 Manager</span>`;
  } else {
    container.innerHTML = `
      <button class="btn btn-secondary btn-sm" id="btn-leave-orbit" style="border-color:rgba(248,113,113,0.3); color:var(--red);">
        Leave Orbit
      </button>
    `;
    
    container.querySelector('#btn-leave-orbit')?.addEventListener('click', async () => {
      const confirmLeave = confirm(`Are you sure you want to leave "${orbit.name}"?`);
      if (!confirmLeave) return;

      try {
        const userRef = doc(db, 'users', user.uid);
        const memberRef = doc(db, 'circles', orbit.id, 'members', user.uid);
        const circleRef = doc(db, 'circles', orbit.id);

        await deleteDoc(memberRef);
        await updateDoc(circleRef, { memberCount: increment(-1) });
        await updateDoc(userRef, { joinedCircles: arrayRemove(orbit.id) });

        showToast(`You have left "${orbit.name}"`, 'info');
        window.location.hash = '#/orbits';
      } catch (err) {
        console.error('[Cosmos Orbits] Leave failed:', err);
        showToast('Failed to leave orbit', 'error');
      }
    });
  }
}

function renderActiveTabContent(outlet, orbit, tabName) {
  const container = outlet.querySelector('#orbit-detail-tab-content');
  if (!container) return;

  const user = auth.currentUser;

  if (tabName === 'feed') {
    container.innerHTML = `
      <div class="orbit-feed-compose">
        <div class="orbit-feed-compose-row">
          <div class="avatar avatar-sm" id="feed-user-avatar" style="font-size: 0.72rem; font-weight: bold;">
          </div>
          <textarea id="orbit-post-input" placeholder="Share something with this orbit..." maxlength="1000"></textarea>
        </div>
        <div style="display:flex; justify-content:flex-end;">
          <button class="btn btn-primary btn-sm" id="btn-submit-orbit-post" disabled>Post</button>
        </div>
      </div>
      <div id="orbit-feed-posts" class="stagger">
        <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
      </div>
    `;

    const userProfile = window.cosmosApp.userProfile || {};
    const avatarContainer = container.querySelector('#feed-user-avatar');
    if (avatarContainer) {
      const name = userProfile.name || user.displayName || 'Builder';
      const initials = name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) || 'U';
      const avatarUrl = userProfile.avatarUrl || user.photoURL || '';
      const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#d97706,#fbbf24)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#db2777,#f472b6)'];
      const colorIdx = user.uid.charCodeAt(0) || 0;
      
      if (avatarUrl) {
        avatarContainer.innerHTML = `<img src="${avatarUrl}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />`;
      } else {
        avatarContainer.style.background = avatarColors[colorIdx % avatarColors.length];
        avatarContainer.textContent = initials;
      }
    }

    const postInput = container.querySelector('#orbit-post-input');
    const submitBtn = container.querySelector('#btn-submit-orbit-post');

    postInput.addEventListener('input', () => {
      submitBtn.disabled = !postInput.value.trim();
    });

    submitBtn.addEventListener('click', async () => {
      const content = postInput.value.trim();
      if (!content) return;

      submitBtn.disabled = true;
      submitBtn.textContent = 'Posting...';

      try {
        const postsCol = collection(db, 'circles', orbit.id, 'posts');
        await addDoc(postsCol, {
          authorId: user.uid,
          authorName: userProfile.name || user.displayName || 'Builder',
          authorHeadline: userProfile.headline || userProfile.role || 'Cosmos Member',
          authorAvatarUrl: userProfile.avatarUrl || user.photoURL || '',
          content,
          createdAt: serverTimestamp()
        });

        postInput.value = '';
        submitBtn.disabled = true;
        submitBtn.textContent = 'Post';
        showToast('Shared with the orbit! ✨', 'success');
      } catch (err) {
        console.error('[Cosmos Orbits] Post error:', err);
        showToast('Failed to post: ' + err.message, 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Post';
      }
    });

    if (unsubPosts) unsubPosts();
    const postsQuery = query(
      collection(db, 'circles', orbit.id, 'posts'),
      orderBy('createdAt', 'desc')
    );

    unsubPosts = onSnapshot(postsQuery, (snapshot) => {
      const postsListEl = container.querySelector('#orbit-feed-posts');
      if (!postsListEl) return;

      const posts = [];
      snapshot.forEach(docSnap => {
        posts.push({ id: docSnap.id, ...docSnap.data() });
      });

      if (posts.length === 0) {
        postsListEl.innerHTML = `
          <div class="empty-state">
            <div class="empty-state-icon">💬</div>
            <h3 class="empty-state-title">No Posts Yet</h3>
            <p class="empty-state-desc">Be the first to share an update or question in this orbit!</p>
          </div>
        `;
        return;
      }

      const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#d97706,#fbbf24)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#db2777,#f472b6)'];

      postsListEl.innerHTML = posts.map((post, i) => {
        const hasPhoto = !!post.authorAvatarUrl;
        const initials = post.authorName ? post.authorName.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) : 'U';
        const colorIdx = post.authorId ? post.authorId.charCodeAt(0) : 0;
        const timeString = formatTime(post.createdAt);
        const canDelete = post.authorId === user.uid || orbit.createdBy === user.uid;

        return `
          <div class="post-card anim-fade-up" style="animation-delay:${i * 0.05}s;">
            <div class="post-header">
              <div class="avatar avatar-sm" style="${hasPhoto ? '' : 'background:' + avatarColors[colorIdx % avatarColors.length]}">
                ${hasPhoto ? `<img src="${post.authorAvatarUrl}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />` : initials}
              </div>
              <div>
                <div class="post-author-name">${escapeHTML(post.authorName || 'Anonymous')}</div>
                <div class="post-author-role">${escapeHTML(post.authorHeadline || 'Cosmos Member')}</div>
              </div>
              <div style="margin-left:auto; display:flex; align-items:center; gap:0.5rem;">
                <span class="post-time">${timeString}</span>
                ${canDelete ? `
                  <button class="btn-delete-post" data-post-id="${post.id}" style="color:var(--red);background:none;border:none;cursor:pointer;padding:4px;display:flex;align-items:center;opacity:0.6;transition:opacity 0.2s;" title="Delete post">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>
                  </button>
                ` : ''}
              </div>
            </div>
            <div class="post-content" style="margin-top:0.75rem;">${escapeHTML(post.content).replace(/\n/g, '<br>')}</div>
          </div>
        `;
      }).join('');

      function escapeHTML(str) {
        if (!str) return '';
        return str.replace(/[&<>'"]/g, 
          tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)
        );
      }

      postsListEl.querySelectorAll('.btn-delete-post').forEach(btn => {
        btn.addEventListener('click', async (e) => {
          e.stopPropagation();
          const postId = btn.dataset.postId;
          const confirmDelete = confirm('Are you sure you want to delete this post?');
          if (!confirmDelete) return;

          try {
            const postRef = doc(db, 'circles', orbit.id, 'posts', postId);
            await deleteDoc(postRef);
            showToast('Post deleted', 'success');
          } catch (err) {
            console.error('[Cosmos Orbits] Delete post error:', err);
            showToast('Failed to delete post: ' + err.message, 'error');
          }
        });

        btn.addEventListener('mouseenter', () => btn.style.opacity = '1');
        btn.addEventListener('mouseleave', () => btn.style.opacity = '0.6');
      });
    }, (error) => {
      console.error('[Cosmos Orbits] Feed snapshot error:', error);
      const postsListEl = container.querySelector('#orbit-feed-posts');
      if (postsListEl) {
        postsListEl.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load posts: ${error.message}</div>`;
      }
    });

  } else if (tabName === 'members') {
    container.innerHTML = `
      <div id="orbit-members-list" class="stagger">
        <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
      </div>
    `;

    if (unsubMembers) unsubMembers();
    unsubMembers = onSnapshot(collection(db, 'circles', orbit.id, 'members'), async (snapshot) => {
      const listEl = container.querySelector('#orbit-members-list');
      if (!listEl) return;

      const members = [];
      snapshot.forEach(d => {
        const data = d.data();
        if (data.status === 'APPROVED') {
          members.push({ id: d.id, ...data });
        }
      });

      if (members.length === 0) {
        listEl.innerHTML = `
          <div style="text-align:center;padding:2rem;color:var(--text-muted);">No members in this orbit yet.</div>
        `;
        return;
      }

      await Promise.all(members.map(async (m) => {
        m.profile = await getOrFetchUserProfile(m.id) || { name: 'Cosmos Member', headline: 'Builder' };
      }));

      members.sort((a, b) => {
        if (a.id === orbit.createdBy) return -1;
        if (b.id === orbit.createdBy) return 1;
        return (a.profile.name || '').localeCompare(b.profile.name || '');
      });

      const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#d97706,#fbbf24)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#db2777,#f472b6)'];

      listEl.innerHTML = members.map((m, i) => {
        const profile = m.profile;
        const hasPhoto = !!profile.avatarUrl;
        const initials = profile.name ? profile.name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) : 'U';
        const colorIdx = m.id.charCodeAt(0) || 0;
        const isOrbitManager = m.id === orbit.createdBy;
        const isCurrentUserCreator = orbit.createdBy === user.uid;

        return `
          <div class="orbit-member-item anim-fade-up" style="animation-delay:${i * 0.04}s;">
            <div class="avatar" style="${hasPhoto ? '' : 'background:' + avatarColors[colorIdx % avatarColors.length]}">
              ${hasPhoto ? `<img src="${profile.avatarUrl}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />` : initials}
            </div>
            <div class="orbit-member-info">
              <div class="orbit-member-name">
                ${escapeHTML(profile.name || 'Cosmos Member')}
                ${isOrbitManager ? `<span class="badge badge-purple" style="font-size:0.65rem;margin-left:0.25rem;">👑 Manager</span>` : ''}
              </div>
              <div class="orbit-member-headline">${escapeHTML(profile.headline || profile.role || 'Cosmos Builder')}</div>
            </div>
            ${isCurrentUserCreator && !isOrbitManager ? `
              <button class="btn btn-secondary btn-sm btn-remove-member" data-member-id="${m.id}" style="border-color:rgba(248,113,113,0.3); color:var(--red); padding:4px 8px; font-size:0.75rem;">
                Remove
              </button>
            ` : ''}
          </div>
        `;
      }).join('');

      function escapeHTML(str) {
        if (!str) return '';
        return str.replace(/[&<>'"]/g, 
          tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)
        );
      }

      listEl.querySelectorAll('.btn-remove-member').forEach(btn => {
        btn.addEventListener('click', async (e) => {
          e.stopPropagation();
          const memberId = btn.dataset.memberId;
          const memberObj = members.find(m => m.id === memberId);
          const name = memberObj ? memberObj.profile.name : 'this user';
          const confirmRemove = confirm(`Are you sure you want to remove "${name}" from this orbit?`);
          if (!confirmRemove) return;

          try {
            const memberDocRef = doc(db, 'circles', orbit.id, 'members', memberId);
            const userDocRef = doc(db, 'users', memberId);
            const circleRef = doc(db, 'circles', orbit.id);

            await deleteDoc(memberDocRef);
            await updateDoc(circleRef, { memberCount: increment(-1) });
            await updateDoc(userDocRef, { joinedCircles: arrayRemove(orbit.id) });

            showToast(`Removed "${name}" from the orbit.`, 'success');
          } catch (err) {
            console.error('[Cosmos Orbits] Remove member error:', err);
            showToast('Failed to remove member: ' + err.message, 'error');
          }
        });
      });
    }, (error) => {
      console.error('[Cosmos Orbits] Members snapshot error:', error);
      const listEl = container.querySelector('#orbit-members-list');
      if (listEl) {
        listEl.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load members: ${error.message}</div>`;
      }
    });

  } else if (tabName === 'manage') {
    if (orbit.createdBy !== user.uid) {
      container.innerHTML = `<div style="text-align:center;padding:2rem;color:var(--red);">Access Denied</div>`;
      return;
    }

    container.innerHTML = `
      <div style="margin-bottom:1.5rem;">
        <h3 style="font-family:var(--font-display);font-size:1.05rem;font-weight:700;margin-bottom:0.75rem;">Pending Join Requests</h3>
        <div id="orbit-requests-list" class="stagger">
          <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
        </div>
      </div>

      <div class="profile-section" style="margin-bottom:1.5rem;">
        <h3 style="font-family:var(--font-display);font-size:1.05rem;font-weight:700;margin-bottom:0.75rem;">Edit Orbit Details</h3>
        <form id="edit-orbit-form">
          <div class="form-group">
            <label class="form-label" for="edit-orbit-name">Orbit Name</label>
            <input class="form-input" type="text" id="edit-orbit-name" required value="${escapeHTML(orbit.name)}" />
          </div>
          <div class="form-group">
            <label class="form-label" for="edit-orbit-desc">Description</label>
            <textarea class="form-input" id="edit-orbit-desc" required style="min-height:90px;">${escapeHTML(orbit.description)}</textarea>
          </div>
          <div class="form-group">
            <label class="form-label" for="edit-orbit-theme">Theme / Category</label>
            <input class="form-input" type="text" id="edit-orbit-theme" required value="${escapeHTML(orbit.theme)}" />
          </div>
          <div class="form-group">
            <label class="form-label" for="edit-orbit-tags">Tags (comma-separated)</label>
            <input class="form-input" type="text" id="edit-orbit-tags" value="${escapeHTML(orbit.tags ? orbit.tags.join(', ') : '')}" />
          </div>
          <div class="switch-group" style="margin-top:1rem; margin-bottom:1rem;">
            <div class="switch-label-wrap">
              <span class="switch-title">Make Private</span>
              <span class="switch-description">Only approved members can view posts and members</span>
            </div>
            <label class="switch">
              <input type="checkbox" id="edit-orbit-is-private" ${orbit.isPrivate ? 'checked' : ''} />
              <span class="slider"></span>
            </label>
          </div>
          <button class="btn btn-primary btn-sm" id="btn-save-orbit-changes" type="submit">Save Changes</button>
        </form>
      </div>

      <div class="danger-zone-box">
        <h4 class="danger-zone-title">Danger Zone</h4>
        <p class="danger-zone-desc">Once you delete an orbit, there is no going back. All discussions, posts, and memberships will be deleted permanently.</p>
        <button class="btn btn-sm btn-primary" id="btn-delete-orbit" style="background:var(--gradient-danger);">Delete Orbit</button>
      </div>
    `;

    function escapeHTML(str) {
      if (!str) return '';
      return str.replace(/[&<>'"]/g, 
        tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)
      );
    }

    const editForm = container.querySelector('#edit-orbit-form');
    const saveBtn = container.querySelector('#btn-save-orbit-changes');
    editForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const newName = container.querySelector('#edit-orbit-name').value.trim();
      const newDesc = container.querySelector('#edit-orbit-desc').value.trim();
      const newTheme = container.querySelector('#edit-orbit-theme').value.trim();
      const newTagsInput = container.querySelector('#edit-orbit-tags').value;
      const newIsPrivate = container.querySelector('#edit-orbit-is-private').checked;
      const newTags = newTagsInput ? newTagsInput.split(',').map(t => t.trim()).filter(Boolean) : [newTheme || 'Community'];

      if (!newName || !newDesc || !newTheme) {
        showToast('Please fill in all required fields.', 'error');
        return;
      }

      saveBtn.disabled = true;
      saveBtn.textContent = 'Saving...';

      try {
        const circleRef = doc(db, 'circles', orbit.id);
        await updateDoc(circleRef, {
          name: newName,
          description: newDesc,
          theme: newTheme,
          tags: newTags,
          isPrivate: newIsPrivate
        });
        showToast('Orbit details updated! ✨', 'success');
      } catch (err) {
        console.error('[Cosmos Orbits] Edit error:', err);
        showToast('Failed to update details: ' + err.message, 'error');
      } finally {
        saveBtn.disabled = false;
        saveBtn.textContent = 'Save Changes';
      }
    });

    container.querySelector('#btn-delete-orbit').addEventListener('click', async () => {
      const confirmDelete = confirm(`Are you sure you want to delete the orbit "${orbit.name}"?\nThis cannot be undone!`);
      if (!confirmDelete) return;

      const confirmName = prompt(`To verify, type the name of the orbit: "${orbit.name}"`);
      if (confirmName !== orbit.name) {
        showToast('Verification failed. Orbit not deleted.', 'error');
        return;
      }

      try {
        cleanupOrbitDetail();
        const circleRef = doc(db, 'circles', orbit.id);
        await deleteDoc(circleRef);
        showToast(`Orbit "${orbit.name}" deleted.`, 'info');
        window.location.hash = '#/orbits';
      } catch (err) {
        console.error('[Cosmos Orbits] Delete orbit error:', err);
        showToast('Failed to delete orbit: ' + err.message, 'error');
      }
    });

    if (unsubMembers) unsubMembers();
    unsubMembers = onSnapshot(collection(db, 'circles', orbit.id, 'members'), async (snapshot) => {
      const reqListEl = container.querySelector('#orbit-requests-list');
      if (!reqListEl) return;

      const pending = [];
      snapshot.forEach(d => {
        const data = d.data();
        if (data.status === 'PENDING') {
          pending.push({ id: d.id, ...data });
        }
      });

      if (pending.length === 0) {
        reqListEl.innerHTML = `
          <div style="padding:1rem; border:1px dashed var(--border); border-radius:var(--radius); text-align:center; color:var(--text-muted); font-size:0.85rem;">
            No pending join requests.
          </div>
        `;
        return;
      }

      await Promise.all(pending.map(async (p) => {
        p.profile = await getOrFetchUserProfile(p.id) || { name: 'Cosmos User', headline: 'Builder' };
      }));

      const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#d97706,#fbbf24)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#db2777,#f472b6)'];

      reqListEl.innerHTML = pending.map((p, i) => {
        const profile = p.profile;
        const hasPhoto = !!profile.avatarUrl;
        const initials = profile.name ? profile.name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) : 'U';
        const colorIdx = p.id.charCodeAt(0) || 0;

        return `
          <div class="orbit-request-item anim-fade-up" style="animation-delay:${i * 0.04}s;">
            <div class="avatar avatar-sm" style="${hasPhoto ? '' : 'background:' + avatarColors[colorIdx % avatarColors.length]}">
              ${hasPhoto ? `<img src="${profile.avatarUrl}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />` : initials}
            </div>
            <div class="orbit-request-info">
              <div class="orbit-request-name">${escapeHTML(profile.name || 'Applicant')}</div>
              <div class="orbit-request-headline">${escapeHTML(profile.headline || profile.role || 'Cosmos Builder')}</div>
            </div>
            <div style="display:flex; gap:0.35rem;">
              <button class="btn btn-primary btn-sm btn-approve-request" data-user-id="${p.id}" style="background:var(--gradient-success); padding:4px 8px; font-size:0.75rem;">Approve</button>
              <button class="btn btn-secondary btn-sm btn-reject-request" data-user-id="${p.id}" style="border-color:rgba(248,113,113,0.3); color:var(--red); padding:4px 8px; font-size:0.75rem;">Reject</button>
            </div>
          </div>
        `;
      }).join('');

      function escapeHTML(str) {
        if (!str) return '';
        return str.replace(/[&<>'"]/g, 
          tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)
        );
      }

      reqListEl.querySelectorAll('.btn-approve-request').forEach(btn => {
        btn.addEventListener('click', async (e) => {
          e.stopPropagation();
          const applicantId = btn.dataset.userId;
          const applicant = pending.find(p => p.id === applicantId);
          const name = applicant ? applicant.profile.name : 'this builder';

          btn.disabled = true;
          btn.textContent = 'Approving...';

          try {
            const memberDocRef = doc(db, 'circles', orbit.id, 'members', applicantId);
            const userDocRef = doc(db, 'users', applicantId);
            const circleRef = doc(db, 'circles', orbit.id);

            await updateDoc(memberDocRef, { status: 'APPROVED' });
            await updateDoc(circleRef, { memberCount: increment(1) });
            await updateDoc(userDocRef, {
              joinedCircles: arrayUnion(orbit.id),
              pendingCircles: arrayRemove(orbit.id)
            });

            await addDoc(collection(db, 'notifications'), {
              userId: applicantId,
              type: 'COMMUNITY_ANNOUNCEMENT',
              title: 'Orbit Request Approved! 🌐',
              body: `You have been approved to join the orbit "${orbit.name}".`,
              timestamp: serverTimestamp(),
              isRead: false,
              actionId: orbit.id
            });

            showToast(`Approved "${name}" successfully! 🚀`, 'success');
          } catch (err) {
            console.error('[Cosmos Orbits] Approve error:', err);
            showToast('Failed to approve request: ' + err.message, 'error');
            btn.disabled = false;
            btn.textContent = 'Approve';
          }
        });
      });

      reqListEl.querySelectorAll('.btn-reject-request').forEach(btn => {
        btn.addEventListener('click', async (e) => {
          e.stopPropagation();
          const applicantId = btn.dataset.userId;
          const applicant = pending.find(p => p.id === applicantId);
          const name = applicant ? applicant.profile.name : 'this builder';

          const confirmReject = confirm(`Are you sure you want to reject "${name}"'s request?`);
          if (!confirmReject) return;

          btn.disabled = true;
          btn.textContent = 'Rejecting...';

          try {
            const memberDocRef = doc(db, 'circles', orbit.id, 'members', applicantId);
            const userDocRef = doc(db, 'users', applicantId);

            await deleteDoc(memberDocRef);
            await updateDoc(userDocRef, {
              pendingCircles: arrayRemove(orbit.id)
            });

            showToast(`Rejected request from "${name}"`, 'info');
          } catch (err) {
            console.error('[Cosmos Orbits] Reject error:', err);
            showToast('Failed to reject request: ' + err.message, 'error');
            btn.disabled = false;
            btn.textContent = 'Reject';
          }
        });
      });
    }, (error) => {
      console.error('[Cosmos Orbits] Pending requests error:', error);
      const reqListEl = container.querySelector('#orbit-requests-list');
      if (reqListEl) {
        reqListEl.innerHTML = `<div style="text-align:center;color:var(--red);padding:1rem;">Failed to load requests</div>`;
      }
    });
  }
}
