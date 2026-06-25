/* ============================================================
   Cosmos PWA — Settings and Network Relations Page
   ============================================================ */

import { auth, db, doc, getDoc, updateDoc, deleteDoc, collection, query, where, onSnapshot, increment, serverTimestamp, updatePassword, reauthenticateWithCredential, EmailAuthProvider } from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

let connUnsubscribe = null;
let incomingUnsubscribe = null;
let outgoingUnsubscribe = null;
let userUnsubscribe = null;

const mockFollowers = [];
const mockFollowing = [];
const mockConnections = [];

export async function renderSettings(outlet) {
  const user = auth.currentUser;
  if (!user) {
    router.navigate('/auth');
    return;
  }

  outlet.innerHTML = `
    <div class="settings-page page">
      <div class="sub-page-header">
        <button class="btn-back" id="btn-settings-back" aria-label="Go back">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        </button>
        <h1 class="page-title">Control Center</h1>
      </div>

      <div class="settings-section anim-fade-up" style="animation-delay:0.05s;padding-top:1.25rem;">
        <span class="settings-section-title">Network Relations</span>
        <div class="settings-dashboard-grid">
          <div class="dashboard-tile" id="tile-followers">
            <div class="dashboard-tile-value" id="val-followers" style="color:var(--purple);">-</div>
            <div class="dashboard-tile-label">Followers</div>
          </div>
          <div class="dashboard-tile" id="tile-following">
            <div class="dashboard-tile-value" id="val-following" style="color:var(--blue);">-</div>
            <div class="dashboard-tile-label">Following</div>
          </div>
          <div class="dashboard-tile" id="tile-connections">
            <div class="dashboard-tile-value" id="val-connections" style="color:var(--teal);">-</div>
            <div class="dashboard-tile-label">Connections</div>
          </div>
        </div>
      </div>

      <div class="settings-section anim-fade-up" style="animation-delay:0.1s;">
        <span class="settings-section-title">Account</span>
        <div class="settings-card">
          <div class="settings-item" id="item-edit-profile">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg></div>
            <div class="settings-item-label">Edit Profile</div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-change-password">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg></div>
            <div class="settings-item-label">Change Password</div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-linkedin">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.779-1.75-1.75s.784-1.75 1.75-1.75 1.75.779 1.75 1.75-.784 1.75-1.75 1.75zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z"/></svg></div>
            <div class="settings-item-label">LinkedIn Integration</div>
            <span class="settings-item-value" id="txt-linkedin-status">Loading...</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>
      </div>

      <div class="settings-section anim-fade-up" style="animation-delay:0.12s;">
        <span class="settings-section-title">Support</span>
        <div class="settings-card">
          <div class="settings-item" id="item-help-support">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg></div>
            <div class="settings-item-label">Help & Support</div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>
      </div>

      <div class="settings-section anim-fade-up" style="animation-delay:0.15s;">
        <span class="settings-section-title">Notifications</span>
        <div class="settings-card settings-card-padded">
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">New Matches</div>
              <div class="settings-toggle-desc">Notify when a mutual match is made</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-matches" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Messages</div>
              <div class="settings-toggle-desc">Notify when a new message is received</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-messages" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">AI Summaries</div>
              <div class="settings-toggle-desc">Notify when a meeting summary is ready</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-ai" checked><span class="slider round"></span></label>
          </div>
        </div>
      </div>

      <div class="settings-section anim-fade-up" style="animation-delay:0.2s;">
        <span class="settings-section-title">Privacy</span>
        <div class="settings-card settings-card-padded">
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Profile Visibility</div>
              <div class="settings-toggle-desc">Show profile in matching discovery deck</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-priv-visibility" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Mutual Connections</div>
              <div class="settings-toggle-desc">Display mutual connections to others</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-priv-mutual" checked><span class="slider round"></span></label>
          </div>
        </div>
      </div>

      <div class="settings-section anim-fade-up" style="animation-delay:0.25s;margin-bottom:2rem;">
        <span class="settings-section-title" style="color:var(--red);">Danger Zone</span>
        <div class="settings-card">
          <div class="settings-item settings-item-danger" id="item-logout">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg></div>
            <div class="settings-item-label">Sign Out</div>
          </div>
        </div>
      </div>

      <div class="relations-modal hidden" id="relations-modal" style="position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(6,8,17,0.9);backdrop-filter:blur(20px);z-index:9999;display:flex;flex-direction:column;transition:opacity 0.25s ease;">
        <div class="relations-modal-inner" style="flex:1;display:flex;flex-direction:column;max-width:480px;margin:0 auto;width:100%;background:#060811;border-left:1px solid var(--border);border-right:1px solid var(--border);">
          <div style="display:flex;justify-content:space-between;align-items:center;padding:1.25rem 1rem;border-bottom:1.5px solid var(--border);">
            <h2 id="modal-title" style="margin:0;font-size:1.25rem;font-weight:700;font-family:'Outfit',sans-serif;">Followers</h2>
            <button id="modal-close" style="background:rgba(255,255,255,0.05);border:none;color:var(--text-primary);font-size:1.5rem;cursor:pointer;width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;">✕</button>
          </div>
          <div class="modal-tabs" style="display:flex;padding:0.5rem;border-bottom:1px solid var(--border);background:rgba(255,255,255,0.02);">
            <button class="modal-tab" data-tab="followers" style="flex:1;background:none;border:none;color:var(--text-muted);font-weight:600;padding:0.75rem 0;font-size:0.9rem;cursor:pointer;border-radius:10px;">Followers</button>
            <button class="modal-tab" data-tab="following" style="flex:1;background:none;border:none;color:var(--text-muted);font-weight:600;padding:0.75rem 0;font-size:0.9rem;cursor:pointer;border-radius:10px;">Following</button>
            <button class="modal-tab" data-tab="connections" style="flex:1;background:none;border:none;color:var(--text-muted);font-weight:600;padding:0.75rem 0;font-size:0.9rem;cursor:pointer;border-radius:10px;">Connections</button>
          </div>
          <div style="flex:1;overflow-y:auto;padding:1rem;" id="modal-list-wrap">
            <div id="relations-list" style="display:flex;flex-direction:column;gap:0.75rem;"></div>
            <div id="relations-error" class="hidden" style="text-align:center;padding:3rem 1.5rem;">
              <div style="font-size:2rem;margin-bottom:1rem;">📡</div>
              <p style="font-weight:600;font-size:0.95rem;margin-bottom:1.5rem;" id="error-message">Unable to sync live list. Retrying connection...</p>
              <button class="btn btn-primary btn-sm" id="btn-retry">Retry Connection</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;

  outlet.querySelector('#btn-settings-back').addEventListener('click', () => {
    router.navigate('/profile');
  });

  outlet.querySelector('#item-logout').addEventListener('click', async () => {
    await auth.signOut();
    showToast('Signed out successfully', 'success');
    router.navigate('/auth');
  });

  outlet.querySelector('#item-edit-profile').addEventListener('click', () => {
    router.navigate('/edit-profile/settings');
  });
  outlet.querySelector('#item-help-support').addEventListener('click', () => {
    router.navigate('/help-support');
  });
  outlet.querySelector('#item-change-password').addEventListener('click', () => showChangePasswordModal(outlet, user));
  outlet.querySelector('#item-linkedin').addEventListener('click', () => handleLinkedInToggle(outlet, user.uid));

  const setupToggle = (id, field) => {
    const el = outlet.querySelector(`#${id}`);
    if (!el) return;
    el.addEventListener('change', async (e) => {
      try {
        await updateDoc(doc(db, 'users', user.uid), {
          [field]: e.target.checked,
          updatedAt: serverTimestamp(),
        });
        showToast('Setting updated successfully!', 'success');
      } catch (err) {
        e.target.checked = !e.target.checked;
        showToast('Failed to update setting', 'error');
      }
    });
  };

  try {
    const userSnap = await getDoc(doc(db, 'users', user.uid));
    if (userSnap.exists()) {
      const data = userSnap.data();
      const setChecked = (id, val, fallback = true) => {
        const el = outlet.querySelector(`#${id}`);
        if (el) el.checked = val ?? fallback;
      };
      setChecked('sw-notif-matches', data.notificationNewMatches);
      setChecked('sw-notif-messages', data.notificationMessages);
      setChecked('sw-notif-ai', data.notificationAiSummaries);
      setChecked('sw-priv-visibility', data.privacyProfileVisibility);
      setChecked('sw-priv-mutual', data.privacyShowMutualConnections);
      outlet.querySelector('#txt-linkedin-status').textContent = data.isLinkedInConnected ? 'Connected' : 'Not Connected';
    }
  } catch (e) {
    console.warn('Failed to load settings:', e);
  }

  setupToggle('sw-notif-matches', 'notificationNewMatches');
  setupToggle('sw-notif-messages', 'notificationMessages');
  setupToggle('sw-notif-ai', 'notificationAiSummaries');
  setupToggle('sw-priv-visibility', 'privacyProfileVisibility');
  setupToggle('sw-priv-mutual', 'privacyShowMutualConnections');

  setupRelationsDashboard(outlet, user.uid);

  return () => {
    if (connUnsubscribe) connUnsubscribe();
    if (incomingUnsubscribe) incomingUnsubscribe();
    if (outgoingUnsubscribe) outgoingUnsubscribe();
    if (userUnsubscribe) userUnsubscribe();
    connUnsubscribe = null;
    incomingUnsubscribe = null;
    outgoingUnsubscribe = null;
    userUnsubscribe = null;
    delete window.handleWebRemoveAction;
  };
}

function setupRelationsDashboard(outlet, uid) {
  const tileFollowers = outlet.querySelector('#tile-followers');
  const tileFollowing = outlet.querySelector('#tile-following');
  const tileConnections = outlet.querySelector('#tile-connections');
  const modal = outlet.querySelector('#relations-modal');
  const modalTitle = outlet.querySelector('#modal-title');
  const modalClose = outlet.querySelector('#modal-close');
  const relationsList = outlet.querySelector('#relations-list');
  const relationsError = outlet.querySelector('#relations-error');
  const btnRetry = outlet.querySelector('#btn-retry');

  let activeTab = 'followers';
  let isFetching = true;
  let hasFailed = false;
  let connectionsList = [];
  let incomingRequests = [];
  let outgoingRequests = [];
  const removedUserIds = new Set();

  const highlightActiveTile = () => {
    outlet.querySelectorAll('.dashboard-tile').forEach((tile) => {
      tile.style.transform = '';
      tile.style.borderColor = '';
    });
    const activeTile = outlet.querySelector(`#tile-${activeTab}`);
    if (activeTile) {
      activeTile.style.transform = 'scale(1.05)';
      activeTile.style.borderColor = activeTab === 'followers'
        ? 'var(--purple)'
        : activeTab === 'following'
          ? 'var(--blue)'
          : 'var(--teal)';
    }
  };

  const openModal = (tab) => {
    activeTab = tab;
    modal.classList.remove('hidden');
    modal.style.opacity = '1';
    modalTitle.textContent = tab.charAt(0).toUpperCase() + tab.slice(1);

    outlet.querySelectorAll('.modal-tab').forEach((btn) => {
      btn.style.color = 'var(--text-muted)';
      btn.style.background = 'none';
    });
    const activeBtn = outlet.querySelector(`.modal-tab[data-tab="${tab}"]`);
    if (activeBtn) {
      activeBtn.style.color = 'var(--text-primary)';
      activeBtn.style.background = 'rgba(255,255,255,0.06)';
    }

    highlightActiveTile();
    renderList();
  };

  const closeModal = () => {
    modal.style.opacity = '0';
    setTimeout(() => {
      modal.classList.add('hidden');
      highlightActiveTile();
    }, 250);
  };

  tileFollowers.addEventListener('click', () => openModal('followers'));
  tileFollowing.addEventListener('click', () => openModal('following'));
  tileConnections.addEventListener('click', () => openModal('connections'));
  modalClose.addEventListener('click', closeModal);

  outlet.querySelectorAll('.modal-tab').forEach((btn) => {
    btn.addEventListener('click', () => openModal(btn.dataset.tab));
  });

  window.handleWebRemoveAction = async (memberId, tab) => {
    removedUserIds.add(memberId);
    updateCountsDisplay();

    const row = relationsList.querySelector(`.relation-row[data-id="${memberId}"]`);
    if (row) {
      row.style.transition = 'opacity 0.3s ease, transform 0.3s ease, max-height 0.3s ease, margin 0.3s ease';
      row.style.opacity = '0';
      row.style.transform = 'translateX(-20px)';
      row.style.maxHeight = '0';
      row.style.margin = '0';
      setTimeout(() => {
        row.remove();
        renderList();
      }, 300);
    }

    if (memberId.startsWith('mock_user_')) return;

    try {
      const connectionId = uid < memberId ? `${uid}_${memberId}` : `${memberId}_${uid}`;
      if (tab === 'connections' || tab === 'followers' || tab === 'following') {
        await deleteDoc(doc(db, 'connections', connectionId));
        await updateDoc(doc(db, 'users', uid), {
          connectionsCount: increment(-1),
          followersCount: increment(-1),
          followingCount: increment(-1),
        });
        await updateDoc(doc(db, 'users', memberId), {
          connectionsCount: increment(-1),
          followersCount: increment(-1),
          followingCount: increment(-1),
        });
      }
    } catch (e) {
      console.warn('Firestore remove failed:', e);
    }
  };

  const startListeners = () => {
    isFetching = true;
    hasFailed = false;
    relationsError.classList.add('hidden');
    relationsList.classList.remove('hidden');
    renderList();

    try {
      const connQuery = query(collection(db, 'connections'), where('members', 'array-contains', uid));
      connUnsubscribe = onSnapshot(connQuery, async (snapshot) => {
        const listPromises = snapshot.docs.map(async (d) => {
          const data = d.data();
          const otherId = data.members.find((m) => m !== uid) || '';
          let profile = { id: otherId, name: `Member ${otherId}`, headline: '', avatarUrl: '' };
          try {
            const userSnap = await getDoc(doc(db, 'users', otherId));
            if (userSnap.exists()) {
              const uData = userSnap.data();
              profile = {
                id: otherId,
                name: uData.name || profile.name,
                headline: uData.headline || '',
                avatarUrl: uData.avatarUrl || '',
                isLinkedInConnected: uData.isLinkedInConnected || false,
              };
            }
          } catch (e) {}
          return { id: d.id, member: profile, status: data.status };
        });

        connectionsList = (await Promise.all(listPromises)).filter((item) => item.status === 'ACTIVE');
        isFetching = false;
        updateCountsDisplay();
        renderList();
      }, () => triggerErrorState());

      incomingUnsubscribe = onSnapshot(
        query(collection(db, 'connection_requests'), where('receiverId', '==', uid), where('status', '==', 'PENDING')),
        (snapshot) => {
          incomingRequests = snapshot.docs.map((d) => {
            const data = d.data();
            return {
              id: d.id,
              senderId: data.senderId,
              senderName: data.senderName,
              senderHeadline: data.senderHeadline,
              senderAvatarUrl: data.senderAvatarUrl,
            };
          });
          updateCountsDisplay();
          renderList();
        },
        () => triggerErrorState()
      );

      outgoingUnsubscribe = onSnapshot(
        query(collection(db, 'connection_requests'), where('senderId', '==', uid), where('status', '==', 'PENDING')),
        (snapshot) => {
          outgoingRequests = snapshot.docs.map((d) => {
            const data = d.data();
            return {
              id: d.id,
              receiverId: data.receiverId,
              receiverName: data.receiverName,
              receiverHeadline: data.receiverHeadline,
              receiverAvatarUrl: data.receiverAvatarUrl,
            };
          });
          updateCountsDisplay();
          renderList();
        },
        () => triggerErrorState()
      );

      userUnsubscribe = onSnapshot(doc(db, 'users', uid), (snap) => {
        if (snap.exists()) {
          const data = snap.data();
          outlet.querySelector('#txt-linkedin-status').textContent = data.isLinkedInConnected ? 'Connected' : 'Not Connected';
        }
      });
    } catch (e) {
      triggerErrorState();
    }
  };

  const triggerErrorState = () => {
    isFetching = false;
    hasFailed = true;
    relationsList.classList.add('hidden');
    relationsError.classList.remove('hidden');
  };

  btnRetry.addEventListener('click', () => {
    if (connUnsubscribe) connUnsubscribe();
    if (incomingUnsubscribe) incomingUnsubscribe();
    if (outgoingUnsubscribe) outgoingUnsubscribe();
    if (userUnsubscribe) userUnsubscribe();
    startListeners();
  });

  const updateCountsDisplay = () => {
    const fConns = connectionsList.map((c) => c.member);
    const fReqs = incomingRequests.map((r) => ({
      id: r.senderId,
      name: r.senderName,
      headline: r.senderHeadline,
      avatarUrl: r.senderAvatarUrl,
    }));
    const followers = (connectionsList.length > 0 || incomingRequests.length > 0) ? [...fConns, ...fReqs] : mockFollowers;
    const finalFollowers = followers.filter((f) => !removedUserIds.has(f.id));

    const fgConns = connectionsList.map((c) => c.member);
    const fgReqs = outgoingRequests.map((r) => ({
      id: r.receiverId,
      name: r.receiverName,
      headline: r.receiverHeadline,
      avatarUrl: r.receiverAvatarUrl,
    }));
    const following = (connectionsList.length > 0 || outgoingRequests.length > 0) ? [...fgConns, ...fgReqs] : mockFollowing;
    const finalFollowing = following.filter((f) => !removedUserIds.has(f.id));

    const conns = connectionsList.length > 0 ? connectionsList.map((c) => c.member) : mockConnections;
    const finalConnections = conns.filter((c) => !removedUserIds.has(c.id));

    outlet.querySelector('#val-followers').textContent = finalFollowers.length;
    outlet.querySelector('#val-following').textContent = finalFollowing.length;
    outlet.querySelector('#val-connections').textContent = finalConnections.length;
  };

  const renderList = () => {
    if (hasFailed) return;

    if (isFetching) {
      relationsList.innerHTML = Array(3).fill(0).map(() => `
        <div class="skeleton-row" style="display:flex;align-items:center;gap:1rem;padding:0.5rem 0;">
          <div class="skeleton" style="width:48px;height:48px;border-radius:50%;background:rgba(255,255,255,0.05);"></div>
          <div style="flex:1;display:flex;flex-direction:column;gap:0.4rem;">
            <div class="skeleton" style="width:120px;height:14px;border-radius:4px;background:rgba(255,255,255,0.05);"></div>
            <div class="skeleton" style="width:80px;height:10px;border-radius:3px;background:rgba(255,255,255,0.05);"></div>
          </div>
          <div class="skeleton" style="width:80px;height:32px;border-radius:16px;background:rgba(255,255,255,0.05);"></div>
        </div>
      `).join('');
      return;
    }

    let list = [];
    if (activeTab === 'followers') {
      const fConns = connectionsList.map((c) => c.member);
      const fReqs = incomingRequests.map((r) => ({
        id: r.senderId,
        name: r.senderName,
        headline: r.senderHeadline,
        avatarUrl: r.senderAvatarUrl,
      }));
      list = (connectionsList.length > 0 || incomingRequests.length > 0) ? [...fConns, ...fReqs] : mockFollowers;
    } else if (activeTab === 'following') {
      const fgConns = connectionsList.map((c) => c.member);
      const fgReqs = outgoingRequests.map((r) => ({
        id: r.receiverId,
        name: r.receiverName,
        headline: r.receiverHeadline,
        avatarUrl: r.receiverAvatarUrl,
      }));
      list = (connectionsList.length > 0 || outgoingRequests.length > 0) ? [...fgConns, ...fgReqs] : mockFollowing;
    } else {
      list = connectionsList.length > 0 ? connectionsList.map((c) => c.member) : mockConnections;
    }

    list = list.filter((item) => !removedUserIds.has(item.id));

    if (list.length === 0) {
      relationsList.innerHTML = `
        <div style="text-align:center;padding:3rem 0;color:var(--text-muted);">
          <div style="font-size:2rem;margin-bottom:0.5rem;">✨</div>
          <div>No relationships found</div>
        </div>
      `;
      return;
    }

    relationsList.innerHTML = list.map((item) => {
      const username = '@' + item.name.toLowerCase().replace(/ /g, '');
      const initials = item.name.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2) || 'U';
      const avatarHtml = item.avatarUrl
        ? `<img src="${item.avatarUrl}" alt="${item.name}" style="width:100%;height:100%;object-fit:cover;border-radius:50%;" />`
        : initials;

      let btnHtml = '';
      if (activeTab === 'followers') {
        btnHtml = `<button class="btn btn-outline-danger btn-sm" onclick="handleWebRemoveAction('${item.id}', 'followers')" style="border-radius:18px;padding:0.35rem 1rem;font-size:0.78rem;">Remove</button>`;
      } else if (activeTab === 'following') {
        btnHtml = `
          <button class="btn btn-sm" onclick="handleWebRemoveAction('${item.id}', 'following')" style="border-radius:18px;padding:0.35rem 1rem;font-size:0.78rem;background:rgba(255,255,255,0.08);color:var(--text-primary);border:1px solid rgba(255,255,255,0.15);display:flex;align-items:center;gap:4px;">
            <span style="color:var(--purple);">✓</span> Following
          </button>
        `;
      } else {
        btnHtml = `<button class="btn btn-outline btn-sm" onclick="handleWebRemoveAction('${item.id}', 'connections')" style="border-radius:18px;padding:0.35rem 1rem;font-size:0.78rem;color:var(--text-muted);border-color:var(--border);">Disconnect</button>`;
      }

      return `
        <div class="relation-row" data-id="${item.id}" style="display:flex;align-items:center;gap:1rem;padding:0.5rem 0;height:62px;overflow:hidden;">
          <div class="avatar avatar-md" style="width:48px;height:48px;border-radius:50%;flex-shrink:0;${item.avatarUrl ? '' : 'background:var(--gradient-primary);'}">
            ${avatarHtml}
          </div>
          <div style="flex:1;min-width:0;">
            <div style="font-weight:700;font-size:0.95rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${item.name}</div>
            <div style="font-size:0.78rem;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${username}</div>
          </div>
          <div style="flex-shrink:0;">${btnHtml}</div>
        </div>
      `;
    }).join('');
  };

  startListeners();
}

function showChangePasswordModal(outlet, user) {
  const isGoogleUser = user.providerData.some((p) => p.providerId === 'google.com');
  if (isGoogleUser) {
    showToast('Google sign-in accounts manage passwords through Google.', 'info');
    return;
  }

  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <h3>Change Password</h3>
        <button type="button" class="modal-close" id="modal-pw-close">✕</button>
      </div>
      <form id="change-password-form">
        <div class="form-group">
          <label class="form-label" for="current-password">Current Password</label>
          <input class="form-input" type="password" id="current-password" required autocomplete="current-password" />
        </div>
        <div class="form-group">
          <label class="form-label" for="new-password">New Password</label>
          <input class="form-input" type="password" id="new-password" required minlength="8" autocomplete="new-password" />
        </div>
        <div class="form-group">
          <label class="form-label" for="confirm-password">Confirm New Password</label>
          <input class="form-input" type="password" id="confirm-password" required minlength="8" autocomplete="new-password" />
        </div>
        <p class="form-error hidden" id="pw-error"></p>
        <button type="submit" class="btn btn-primary btn-full" id="btn-update-password">Update Password</button>
      </form>
    </div>
  `;

  const close = () => modal.remove();
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-pw-close').addEventListener('click', close);

  modal.querySelector('#change-password-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const currentPassword = modal.querySelector('#current-password').value;
    const newPassword = modal.querySelector('#new-password').value;
    const confirmPassword = modal.querySelector('#confirm-password').value;
    const errorEl = modal.querySelector('#pw-error');
    const submitBtn = modal.querySelector('#btn-update-password');

    errorEl.classList.add('hidden');
    if (newPassword !== confirmPassword) {
      errorEl.textContent = 'New passwords do not match.';
      errorEl.classList.remove('hidden');
      return;
    }
    if (newPassword.length < 8) {
      errorEl.textContent = 'Password must be at least 8 characters.';
      errorEl.classList.remove('hidden');
      return;
    }

    submitBtn.disabled = true;
    submitBtn.textContent = 'Updating...';

    try {
      const credential = EmailAuthProvider.credential(user.email, currentPassword);
      await reauthenticateWithCredential(user, credential);
      await updatePassword(user, newPassword);
      showToast('Password updated successfully!', 'success');
      close();
    } catch (err) {
      console.error('Password update failed:', err);
      errorEl.textContent = err.code === 'auth/wrong-password'
        ? 'Current password is incorrect.'
        : (err.message || 'Failed to update password.');
      errorEl.classList.remove('hidden');
      submitBtn.disabled = false;
      submitBtn.textContent = 'Update Password';
    }
  });

  document.body.appendChild(modal);
}

async function handleLinkedInToggle(outlet, uid) {
  try {
    const userRef = doc(db, 'users', uid);
    const snap = await getDoc(userRef);
    const connected = snap.exists() && snap.data().isLinkedInConnected;

    if (connected) {
      if (!confirm('Disconnect LinkedIn? This removes your verified credentials and trust badge.')) return;
      await updateDoc(userRef, { isLinkedInConnected: false, updatedAt: serverTimestamp() });
      outlet.querySelector('#txt-linkedin-status').textContent = 'Not Connected';
      showToast('LinkedIn disconnected', 'success');
      return;
    }

    await updateDoc(userRef, { isLinkedInConnected: true, updatedAt: serverTimestamp() });
    outlet.querySelector('#txt-linkedin-status').textContent = 'Connected';
    showToast('LinkedIn connected successfully!', 'success');
  } catch (err) {
    showToast('Failed to update LinkedIn status', 'error');
  }
}
