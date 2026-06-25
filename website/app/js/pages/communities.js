/* ============================================================
   Cosmos PWA — Communities / Orbits Page
   ============================================================ */

import { auth, db, collection, query, onSnapshot, doc, getDoc, setDoc, updateDoc, addDoc, arrayUnion, increment, serverTimestamp } from '../firebase-config.js';
import { showToast } from '../app.js';

export async function renderCommunities(outlet) {
  const user = auth.currentUser;
  if (!user) return;

  // Cleanup old listeners
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
      <div class="page-header">
        <h1 class="page-title">Orbits</h1>
        <p class="page-subtitle">Curated communities for like-minded builders</p>
      </div>
      <div class="search-wrap" style="margin-bottom:1.25rem;">
        <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        <input class="search-input" type="text" id="orbit-search" placeholder="Search orbits..." />
      </div>
      <div id="orbits-list" class="stagger">
        <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
      </div>
    </div>
  `;

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

    // Apply search filter if search input has value
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
    console.error('[Cosmos Communities] Circles snapshot error:', error);
    const container = outlet.querySelector('#orbits-list');
    if (container) {
      container.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load circles: ${error.message}</div>`;
    }
  });

  // Search input change listener
  setTimeout(() => {
    const searchInput = outlet.querySelector('#orbit-search');
    if (searchInput) {
      searchInput.addEventListener('input', () => {
        renderList();
      });
    }
  }, 100);
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
        showToast(`Opening "${orbit.name}" feed...`, 'info');
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
        console.error('[Cosmos Communities] Join error:', err);
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
        showToast(`Viewing "${orbit.name}"`, 'info');
      }
    });
  });
}
