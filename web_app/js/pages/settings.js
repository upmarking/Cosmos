/* ============================================================
   Cosmos PWA — Settings and Network Relations Page
   ============================================================ */

import { auth, db, doc, updateDoc, deleteDoc, collection, query, where, onSnapshot, increment } from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

let connUnsubscribe = null;
let incomingUnsubscribe = null;
let outgoingUnsubscribe = null;
let userUnsubscribe = null;

// Mock fallbacks for demo visual completeness
const mockFollowers = [
  { id: 'mock_user_sarah', name: 'Sarah Jenkins', username: '@sarahjenkins', headline: 'Founder & CEO at BioSphere', initials: 'SJ', isLinkedInConnected: true },
  { id: 'mock_user_elena', name: 'Elena Rostova', username: '@elenarostova', headline: 'Lead Designer at Cosmos Studio', initials: 'ER', isLinkedInConnected: false }
];
const mockFollowing = [
  { id: 'mock_user_david', name: 'David Chen', username: '@davidchen', headline: 'General Partner at Nexus Ventures', initials: 'DC', isLinkedInConnected: true },
  { id: 'mock_user_marcus', name: 'Marcus Vance', username: '@marcusvance', headline: 'VP of Product at ScaleUp', initials: 'MV', isLinkedInConnected: false }
];
const mockConnections = [
  { id: 'mock_user_sarah', name: 'Sarah Jenkins', username: '@sarahjenkins', headline: 'Founder & CEO at BioSphere', initials: 'SJ', isLinkedInConnected: true },
  { id: 'mock_user_david', name: 'David Chen', username: '@davidchen', headline: 'General Partner at Nexus Ventures', initials: 'DC', isLinkedInConnected: true }
];

export async function renderSettings(outlet) {
  const user = auth.currentUser;
  if (!user) {
    router.navigate('/auth');
    return;
  }

  outlet.innerHTML = `
    <div class="settings-page page">
      <!-- Back Header -->
      <div class="settings-header" style="display:flex;align-items:center;padding:1rem;gap:1rem;border-bottom:1.5px solid var(--glass-border);">
        <button class="btn-back" id="btn-settings-back" style="background:none;border:none;color:var(--text);font-size:1.2rem;cursor:pointer;display:flex;align-items:center;justify-content:center;width:36px;height:36px;border-radius:50%;background:rgba(255,255,255,0.05);">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        </button>
        <h1 class="page-title" style="margin:0;font-size:1.25rem;font-weight:600;">Control Center</h1>
      </div>

      <!-- Instagram-Style Network Relations Dashboard -->
      <div class="settings-section anim-fade-up" style="animation-delay:0.05s;padding-top:1.25rem;">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.75rem;padding:0 1rem;">
          <span style="font-size:0.78rem;font-weight:700;color:var(--primary);letter-spacing:1px;text-transform:uppercase;">Network Relations</span>
        </div>
        <div class="settings-dashboard-grid" style="display:grid;grid-template-columns:repeat(3, 1f);gap:0.75rem;padding:0 1rem;">
          <div class="dashboard-tile" id="tile-followers" style="cursor:pointer;text-align:center;background:var(--glass-bg);border:1px solid var(--glass-border);border-radius:16px;padding:1.25rem 0.5rem;transition:transform 0.2s ease, border-color 0.2s ease;">
            <div class="dashboard-tile-value" id="val-followers" style="font-size:1.5rem;font-weight:700;color:var(--primary);line-height:1.2;">-</div>
            <div style="font-size:0.78rem;color:var(--text-muted);margin-top:0.25rem;">Followers</div>
          </div>
          <div class="dashboard-tile" id="tile-following" style="cursor:pointer;text-align:center;background:var(--glass-bg);border:1px solid var(--glass-border);border-radius:16px;padding:1.25rem 0.5rem;transition:transform 0.2s ease, border-color 0.2s ease;">
            <div class="dashboard-tile-value" id="val-following" style="font-size:1.5rem;font-weight:700;color:var(--secondary);line-height:1.2;">-</div>
            <div style="font-size:0.78rem;color:var(--text-muted);margin-top:0.25rem;">Following</div>
          </div>
          <div class="dashboard-tile" id="tile-connections" style="cursor:pointer;text-align:center;background:var(--glass-bg);border:1px solid var(--glass-border);border-radius:16px;padding:1.25rem 0.5rem;transition:transform 0.2s ease, border-color 0.2s ease;">
            <div class="dashboard-tile-value" id="val-connections" style="font-size:1.5rem;font-weight:700;color:var(--tertiary);line-height:1.2;">-</div>
            <div style="font-size:0.78rem;color:var(--text-muted);margin-top:0.25rem;">Connections</div>
          </div>
        </div>
      </div>

      <!-- Account Settings Card -->
      <div class="settings-section anim-fade-up" style="animation-delay:0.1s;margin-top:1.5rem;padding:0 1rem;">
        <span class="settings-section-title" style="font-size:0.78rem;font-weight:700;color:var(--primary);letter-spacing:1px;text-transform:uppercase;display:block;margin-bottom:0.75rem;">Account</span>
        <div class="settings-card" style="background:var(--glass-bg);border:1px solid var(--glass-border);border-radius:16px;overflow:hidden;">
          <div class="settings-item" id="item-edit-profile" style="display:flex;align-items:center;padding:1rem 1.25rem;border-bottom:1px solid var(--glass-border);cursor:pointer;">
            <div style="color:var(--primary);margin-right:1rem;display:flex;"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg></div>
            <div style="flex:1;font-weight:500;">Edit Profile</div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-change-password" style="display:flex;align-items:center;padding:1rem 1.25rem;border-bottom:1px solid var(--glass-border);cursor:pointer;">
            <div style="color:var(--primary);margin-right:1rem;display:flex;"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg></div>
            <div style="flex:1;font-weight:500;">Change Password</div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-linkedin" style="display:flex;align-items:center;padding:1rem 1.25rem;cursor:pointer;">
            <div style="color:var(--primary);margin-right:1rem;display:flex;"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.779-1.75-1.75s.784-1.75 1.75-1.75 1.75.779 1.75 1.75-.784 1.75-1.75 1.75zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z"/></svg></div>
            <div style="flex:1;font-weight:500;">LinkedIn Integration</div>
            <div style="font-size:0.8rem;color:var(--text-muted);margin-right:0.5rem;" id="txt-linkedin-status">Loading...</div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>
      </div>

      <!-- Notifications Card -->
      <div class="settings-section anim-fade-up" style="animation-delay:0.15s;margin-top:1.5rem;padding:0 1rem;">
        <span class="settings-section-title" style="font-size:0.78rem;font-weight:700;color:var(--primary);letter-spacing:1px;text-transform:uppercase;display:block;margin-bottom:0.75rem;">Notifications</span>
        <div class="settings-card" style="background:var(--glass-bg);border:1px solid var(--glass-border);border-radius:16px;overflow:hidden;padding:0.5rem 1.25rem;">
          <div style="display:flex;justify-content:space-between;align-items:center;padding:0.75rem 0;border-bottom:1px solid var(--glass-border);">
            <div>
              <div style="font-weight:500;font-size:0.95rem;">New Matches</div>
              <div style="font-size:0.78rem;color:var(--text-muted);">Notify when a mutual match is made</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-matches" checked><span class="slider round"></span></label>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;padding:0.75rem 0;border-bottom:1px solid var(--glass-border);">
            <div>
              <div style="font-weight:500;font-size:0.95rem;">Messages</div>
              <div style="font-size:0.78rem;color:var(--text-muted);">Notify when a new message is received</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-messages" checked><span class="slider round"></span></label>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;padding:0.75rem 0;">
            <div>
              <div style="font-weight:500;font-size:0.95rem;">AI Summaries</div>
              <div style="font-size:0.78rem;color:var(--text-muted);">Notify when a meeting summary is ready</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-ai" checked><span class="slider round"></span></label>
          </div>
        </div>
      </div>

      <!-- Privacy Card -->
      <div class="settings-section anim-fade-up" style="animation-delay:0.20s;margin-top:1.5rem;padding:0 1rem;">
        <span class="settings-section-title" style="font-size:0.78rem;font-weight:700;color:var(--primary);letter-spacing:1px;text-transform:uppercase;display:block;margin-bottom:0.75rem;">Privacy</span>
        <div class="settings-card" style="background:var(--glass-bg);border:1px solid var(--glass-border);border-radius:16px;overflow:hidden;padding:0.5rem 1.25rem;">
          <div style="display:flex;justify-content:space-between;align-items:center;padding:0.75rem 0;border-bottom:1px solid var(--glass-border);">
            <div>
              <div style="font-weight:500;font-size:0.95rem;">Profile Visibility</div>
              <div style="font-size:0.78rem;color:var(--text-muted);">Show profile in matching discovery deck</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-priv-visibility" checked><span class="slider round"></span></label>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;padding:0.75rem 0;">
            <div>
              <div style="font-weight:500;font-size:0.95rem;">Mutual Connections</div>
              <div style="font-size:0.78rem;color:var(--text-muted);">Display mutual connections to others</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-priv-mutual" checked><span class="slider round"></span></label>
          </div>
        </div>
      </div>

      <!-- Danger Zone Card -->
      <div class="settings-section anim-fade-up" style="animation-delay:0.25s;margin-top:1.5rem;margin-bottom:5rem;padding:0 1rem;">
        <span class="settings-section-title" style="font-size:0.78rem;font-weight:700;color:var(--red);letter-spacing:1px;text-transform:uppercase;display:block;margin-bottom:0.75rem;">Danger Zone</span>
        <div class="settings-card" style="background:var(--glass-bg);border:1px solid var(--glass-border);border-radius:16px;overflow:hidden;">
          <div class="settings-item" id="item-logout" style="display:flex;align-items:center;padding:1rem 1.25rem;cursor:pointer;color:var(--red);">
            <div style="margin-right:1rem;display:flex;"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg></div>
            <div style="flex:1;font-weight:600;">Sign Out</div>
          </div>
        </div>
      </div>

      <!-- Premium Instagram-Style Network Relations Modal overlay -->
      <div class="relations-modal hidden" id="relations-modal" style="position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(6,8,17,0.9);backdrop-filter:blur(20px);z-index:9999;display:flex;flex-direction:column;transition:opacity 0.25s ease;">
        <div class="relations-modal-inner" style="flex:1;display:flex;flex-direction:column;max-width:480px;margin:0 auto;width:100%;background:#060811;border-left:1px solid var(--glass-border);border-right:1px solid var(--glass-border);">
          
          <!-- Modal Header -->
          <div style="display:flex;justify-content:space-between;align-items:center;padding:1.25rem 1rem;border-bottom:1.5px solid var(--glass-border);">
            <h2 id="modal-title" style="margin:0;font-size:1.25rem;font-weight:700;font-family:'Outfit',sans-serif;color:var(--text);">Followers</h2>
            <button id="modal-close" style="background:none;border:none;color:var(--text);font-size:1.5rem;cursor:pointer;width:36px;height:36px;border-radius:50%;background:rgba(255,255,255,0.05);display:flex;align-items:center;justify-content:center;">✕</button>
          </div>

          <!-- Modal Tabs -->
          <div class="modal-tabs" style="display:flex;padding:0.5rem;border-bottom:1px solid var(--glass-border);background:rgba(255,255,255,0.02);">
            <button class="modal-tab" data-tab="followers" style="flex:1;background:none;border:none;color:var(--text-muted);font-weight:600;padding:0.75rem 0;font-size:0.9rem;cursor:pointer;border-radius:10px;transition:all 0.2s;">Followers</button>
            <button class="modal-tab" data-tab="following" style="flex:1;background:none;border:none;color:var(--text-muted);font-weight:600;padding:0.75rem 0;font-size:0.9rem;cursor:pointer;border-radius:10px;transition:all 0.2s;">Following</button>
            <button class="modal-tab" data-tab="connections" style="flex:1;background:none;border:none;color:var(--text-muted);font-weight:600;padding:0.75rem 0;font-size:0.9rem;cursor:pointer;border-radius:10px;transition:all 0.2s;">Connections</button>
          </div>

          <!-- Modal List Container -->
          <div style="flex:1;overflow-y:auto;padding:1rem;" id="modal-list-wrap">
            <div id="relations-list" style="display:flex;flex-direction:column;gap:0.75rem;">
              <!-- Dynamic elements or skeletons rendering here -->
            </div>
            
            <!-- Failure State display -->
            <div id="relations-error" class="hidden" style="text-align:center;padding:3rem 1.5rem;">
              <div style="font-size:2rem;margin-bottom:1rem;">📡</div>
              <p style="font-weight:600;font-size:0.95rem;color:var(--text);margin-bottom:1.5rem;" id="error-message">Unable to sync live list. Retrying connection...</p>
              <button class="btn btn-primary btn-sm" id="btn-retry" style="padding:0.5rem 1.5rem;border-radius:20px;">Retry Connection</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;

  // Attach navigation
  outlet.querySelector('#btn-settings-back').addEventListener('click', () => {
    router.navigate('/profile');
  });

  outlet.querySelector('#item-logout').addEventListener('click', async () => {
    await auth.signOut();
    showToast('Signed out successfully', 'success');
    router.navigate('/auth');
  });

  // Attach sub-settings clicks
  outlet.querySelector('#item-edit-profile').addEventListener('click', () => showToast('Profile editing coming soon!', 'info'));
  outlet.querySelector('#item-change-password').addEventListener('click', () => showToast('Password change coming soon!', 'info'));
  outlet.querySelector('#item-linkedin').addEventListener('click', () => showToast('LinkedIn sync update coming soon!', 'info'));

  // Local storage properties setup for mock toggles
  const setupToggle = (id, property) => {
    const el = outlet.querySelector(`#${id}`);
    if (el) {
      el.addEventListener('change', (e) => {
        showToast('Setting updated successfully!', 'success');
      });
    }
  };
  setupToggle('sw-notif-matches');
  setupToggle('sw-notif-messages');
  setupToggle('sw-notif-ai');
  setupToggle('sw-priv-visibility');
  setupToggle('sw-priv-mutual');

  // Network relations interactive setup
  setupRelationsDashboard(outlet, user.uid);
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

  // Keep a set of removed user IDs locally for immediate/instant feedback
  const removedUserIds = new Set();

  // Highlight active tile helper
  const highlightActiveTile = () => {
    outlet.querySelectorAll('.dashboard-tile').forEach(tile => {
      tile.style.transform = '';
      tile.style.borderColor = 'var(--glass-border)';
    });
    const activeTile = outlet.querySelector(`#tile-${activeTab}`);
    if (activeTile) {
      activeTile.style.transform = 'scale(1.05)';
      activeTile.style.borderColor = `var(--${activeTab === 'followers' ? 'primary' : activeTab === 'following' ? 'secondary' : 'tertiary'})`;
    }
  };

  const openModal = (tab) => {
    activeTab = tab;
    modal.classList.remove('hidden');
    modal.style.opacity = '1';
    modalTitle.textContent = tab.charAt(0).toUpperCase() + tab.slice(1);
    
    // Set active tab styling
    outlet.querySelectorAll('.modal-tab').forEach(btn => {
      btn.style.color = 'var(--text-muted)';
      btn.style.background = 'none';
    });
    const activeBtn = outlet.querySelector(`.modal-tab[data-tab="${tab}"]`);
    if (activeBtn) {
      activeBtn.style.color = 'var(--text)';
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

  // Click listeners on tiles
  tileFollowers.addEventListener('click', () => openModal('followers'));
  tileFollowing.addEventListener('click', () => openModal('following'));
  tileConnections.addEventListener('click', () => openModal('connections'));
  
  modalClose.addEventListener('click', closeModal);

  // Modal tabs click setup
  outlet.querySelectorAll('.modal-tab').forEach(btn => {
    btn.addEventListener('click', () => {
      openModal(btn.dataset.tab);
    });
  });

  // Handle remove/unfollow/disconnect
  window.handleWebRemoveAction = async (memberId, tab) => {
    removedUserIds.add(memberId);
    
    // Decrement counters immediately (Immediate Interactivity!)
    updateCountsDisplay();

    // Fade out row animation instantly
    const row = relationsList.querySelector(`.relation-row[data-id="${memberId}"]`);
    if (row) {
      row.style.transition = 'opacity 0.3s ease, transform 0.3s ease, max-height 0.3s ease, margin 0.3s ease';
      row.style.opacity = '0';
      row.style.transform = 'translateX(-20px)';
      row.style.maxHeight = '0';
      row.style.margin = '0';
      setTimeout(() => {
        row.remove();
        renderList(); // Re-render lists without this row
      }, 300);
    }

    // Call database deletion
    if (memberId.startsWith('mock_user_')) return; // Mock simulation

    try {
      const connectionId = uid < memberId ? `${uid}_${memberId}` : `${memberId}_${uid}`;
      if (tab === 'connections' || tab === 'followers' || tab === 'following') {
        // Delete messages
        const messagesQuery = query(collection(db, 'connections', connectionId, 'messages'));
        // Delete connection doc
        await deleteDoc(doc(db, 'connections', connectionId));
        // Decrement stats in user docs
        await updateDoc(doc(db, 'users', uid), {
          connectionsCount: increment(-1),
          followersCount: increment(-1),
          followingCount: increment(-1)
        });
        await updateDoc(doc(db, 'users', memberId), {
          connectionsCount: increment(-1),
          followersCount: increment(-1),
          followingCount: increment(-1)
        });
      }
    } catch (e) {
      console.warn("Firestore remove failed:", e);
    }
  };

  // Live query sync
  const startListeners = () => {
    isFetching = true;
    hasFailed = false;
    relationsError.classList.add('hidden');
    relationsList.classList.remove('hidden');
    renderList();

    try {
      // 1. Listen to connections
      const connQuery = query(collection(db, 'connections'), where('members', 'array-contains', uid));
      connUnsubscribe = onSnapshot(connQuery, async (snapshot) => {
        const listPromises = snapshot.docs.map(async (d) => {
          const data = d.data();
          const otherId = data.members.find(m => m !== uid) || "";
          
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
                isLinkedInConnected: uData.isLinkedInConnected || false
              };
            }
          } catch(e) {}
          return { id: d.id, member: profile, status: data.status };
        });

        const list = await Promise.all(listPromises);
        connectionsList = list.filter(item => item.status === 'ACTIVE');
        isFetching = false;
        updateCountsDisplay();
        renderList();
      }, (err) => {
        triggerErrorState();
      });

      // 2. Listen to incoming connection requests
      const incomingQuery = query(collection(db, 'connection_requests'), where('receiverId', '==', uid), where('status', '==', 'PENDING'));
      // Note: we listen to connection requests
      incomingUnsubscribe = onSnapshot(query(collection(db, 'connection_requests'), where('receiverId', '==', uid), where('status', '==', 'PENDING')), (snapshot) => {
        incomingRequests = snapshot.docs.map(d => {
          const data = d.data();
          return {
            id: d.id,
            senderId: data.senderId,
            senderName: data.senderName,
            senderHeadline: data.senderHeadline,
            senderAvatarUrl: data.senderAvatarUrl
          };
        });
        updateCountsDisplay();
        renderList();
      }, (err) => {
        triggerErrorState();
      });

      // 3. Listen to outgoing connection requests
      outgoingUnsubscribe = onSnapshot(query(collection(db, 'connection_requests'), where('senderId', '==', uid), where('status', '==', 'PENDING')), (snapshot) => {
        outgoingRequests = snapshot.docs.map(d => {
          const data = d.data();
          return {
            id: d.id,
            receiverId: data.receiverId,
            receiverName: data.receiverName,
            receiverHeadline: data.receiverHeadline,
            receiverAvatarUrl: data.receiverAvatarUrl
          };
        });
        updateCountsDisplay();
        renderList();
      }, (err) => {
        triggerErrorState();
      });

      // 4. Listen to user document for default counts fallback
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

  // Reconnect/Retry setup
  btnRetry.addEventListener('click', () => {
    if (connUnsubscribe) connUnsubscribe();
    if (incomingUnsubscribe) incomingUnsubscribe();
    if (outgoingUnsubscribe) outgoingUnsubscribe();
    if (userUnsubscribe) userUnsubscribe();
    startListeners();
  });

  const updateCountsDisplay = () => {
    // Map list counts
    const fConns = connectionsList.map(c => c.member);
    const fReqs = incomingRequests.map(r => ({ id: r.senderId, name: r.senderName, headline: r.senderHeadline, avatarUrl: r.senderAvatarUrl }));
    const followers = (connectionsList.length > 0 || incomingRequests.length > 0) ? [...fConns, ...fReqs] : mockFollowers;
    const finalFollowers = followers.filter(f => !removedUserIds.has(f.id));

    const fgConns = connectionsList.map(c => c.member);
    const fgReqs = outgoingRequests.map(r => ({ id: r.receiverId, name: r.receiverName, headline: r.receiverHeadline, avatarUrl: r.receiverAvatarUrl }));
    const following = (connectionsList.length > 0 || outgoingRequests.length > 0) ? [...fgConns, ...fgReqs] : mockFollowing;
    const finalFollowing = following.filter(f => !removedUserIds.has(f.id));

    const conns = connectionsList.length > 0 ? connectionsList.map(c => c.member) : mockConnections;
    const finalConnections = conns.filter(c => !removedUserIds.has(c.id));

    outlet.querySelector('#val-followers').textContent = finalFollowers.length;
    outlet.querySelector('#val-following').textContent = finalFollowing.length;
    outlet.querySelector('#val-connections').textContent = finalConnections.length;
  };

  const renderList = () => {
    if (hasFailed) return;

    if (isFetching) {
      // Skeleton loader rows
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

    // Resolve filtered list
    let list = [];
    if (activeTab === 'followers') {
      const fConns = connectionsList.map(c => c.member);
      const fReqs = incomingRequests.map(r => ({ id: r.senderId, name: r.senderName, headline: r.senderHeadline, avatarUrl: r.senderAvatarUrl }));
      list = (connectionsList.length > 0 || incomingRequests.length > 0) ? [...fConns, ...fReqs] : mockFollowers;
    } else if (activeTab === 'following') {
      const fgConns = connectionsList.map(c => c.member);
      const fgReqs = outgoingRequests.map(r => ({ id: r.receiverId, name: r.receiverName, headline: r.receiverHeadline, avatarUrl: r.receiverAvatarUrl }));
      list = (connectionsList.length > 0 || outgoingRequests.length > 0) ? [...fgConns, ...fgReqs] : mockFollowing;
    } else {
      list = connectionsList.length > 0 ? connectionsList.map(c => c.member) : mockConnections;
    }

    // Filter out locally removed items
    list = list.filter(item => !removedUserIds.has(item.id));

    if (list.length === 0) {
      relationsList.innerHTML = `
        <div style="text-align:center;padding:3rem 0;color:var(--text-muted);">
          <div style="font-size:2rem;margin-bottom:0.5rem;">✨</div>
          <div>No relationships found</div>
        </div>
      `;
      return;
    }

    relationsList.innerHTML = list.map(item => {
      const username = '@' + item.name.toLowerCase().replace(/ /g, '');
      const initials = item.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0,2) || 'U';
      const avatarHtml = item.avatarUrl 
        ? `<img src="${item.avatarUrl}" alt="${item.name}" style="width:100%;height:100%;object-fit:cover;border-radius:50%;" />`
        : initials;

      let btnHtml = '';
      if (activeTab === 'followers') {
        btnHtml = `<button class="btn btn-outline-danger btn-sm" onclick="handleWebRemoveAction('${item.id}', 'followers')" style="border-radius:18px;padding:0.35rem 1rem;font-size:0.78rem;">Remove</button>`;
      } else if (activeTab === 'following') {
        // Toggle style "Following" with active indicators
        btnHtml = `
          <button class="btn btn-sm" onclick="handleWebRemoveAction('${item.id}', 'following')" style="border-radius:18px;padding:0.35rem 1rem;font-size:0.78rem;background:rgba(255,255,255,0.08);color:var(--text);border:1px solid rgba(255,255,255,0.15);display:flex;align-items:center;gap:4px;">
            <span style="color:var(--primary);">✓</span> Following
          </button>
        `;
      } else {
        btnHtml = `<button class="btn btn-outline btn-sm" onclick="handleWebRemoveAction('${item.id}', 'connections')" style="border-radius:18px;padding:0.35rem 1rem;font-size:0.78rem;color:var(--text-muted);border-color:var(--glass-border);">Disconnect</button>`;
      }

      return `
        <div class="relation-row" data-id="${item.id}" style="display:flex;align-items:center;gap:1rem;padding:0.5rem 0;height:62px;overflow:hidden;">
          <div class="avatar avatar-md" style="width:48px;height:48px;border-radius:50%;flex-shrink:0;${item.avatarUrl ? '' : 'background:var(--gradient-primary);'}">
            ${avatarHtml}
          </div>
          <div style="flex:1;min-width:0;">
            <div style="font-weight:700;font-size:0.95rem;color:var(--text);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${item.name}</div>
            <div style="font-size:0.78rem;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${username}</div>
          </div>
          <div style="flex-shrink:0;">
            ${btnHtml}
          </div>
        </div>
      `;
    }).join('');
  };

  // Start live snapshots
  startListeners();
}
