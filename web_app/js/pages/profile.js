/* ============================================================
   Cosmos PWA — Profile Page
   ============================================================ */

import { auth, signOut, db, doc, getDoc } from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

export async function renderProfile(outlet) {
  const user = window.cosmosApp.user || auth.currentUser;
  let profileData = window.cosmosApp.userProfile || {};

  if (user) {
    try {
      const snap = await getDoc(doc(db, 'users', user.uid));
      if (snap.exists()) {
        profileData = snap.data();
        window.cosmosApp.userProfile = profileData;
      }
    } catch (e) {
      console.error('Error fetching user profile:', e);
    }
  }

  const displayName = profileData.name || user?.displayName || user?.email?.split('@')[0] || 'Builder';
  const initials = displayName.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2) || 'U';
  const photoURL = profileData.avatarUrl || user?.photoURL;
  const headline = profileData.headline || profileData.role || 'Cosmos Builder';
  const connectionsCount = profileData.connectionsCount || 0;
  const followersCount = profileData.followersCount || 0;
  const followingCount = profileData.followingCount || 0;
  const membershipTier = profileData.membershipTier || 'EXPLORER';
  const tags = profileData.tags || [];
  const bio = profileData.bio || '';
  const company = profileData.company || '';
  const location = profileData.location || '';
  const primaryUserType = profileData.primaryUserType || '';
  const isProfileComplete = profileData.isProfileComplete || !!(profileData.name && primaryUserType);

  const tierColors = {
    EXPLORER: 'badge-blue',
    MEMBER: 'badge-green',
    INNER_CIRCLE: 'badge-purple',
    FOUNDER: 'badge-purple',
  };
  const tierBadge = membershipTier.replace('_', ' ');

  const metaItems = [];
  if (primaryUserType) metaItems.push(`<span class="profile-meta-item">${escapeHtml(primaryUserType)}</span>`);
  if (company) metaItems.push(`<span class="profile-meta-item">🏢 ${escapeHtml(company)}</span>`);
  if (location) metaItems.push(`<span class="profile-meta-item">📍 ${escapeHtml(location)}</span>`);

  outlet.innerHTML = `
    <div class="profile-page page">
      <div class="profile-hero anim-fade-up">
        <div class="profile-hero-bg"></div>
        <div class="profile-avatar-wrap">
          <div class="avatar avatar-xl" style="${photoURL ? '' : 'background:var(--gradient-primary);'}">
            ${photoURL ? `<img src="${photoURL}" alt="${escapeHtml(displayName)}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />` : initials}
          </div>
        </div>
        <div class="profile-name">${escapeHtml(displayName)}</div>
        <div class="profile-headline">${escapeHtml(headline)}</div>
        ${metaItems.length ? `<div class="profile-meta">${metaItems.join('')}</div>` : ''}
        ${bio ? `<div class="profile-about"><p class="profile-about-text">${escapeHtml(bio)}</p></div>` : ''}
        <div style="display:flex;gap:0.5rem;justify-content:center;margin-top:0.75rem;">
          <span class="badge ${tierColors[membershipTier] || 'badge-purple'}">🚀 ${tierBadge}</span>
          ${profileData.isLinkedInConnected ? `
          <span class="badge badge-blue">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="#0a66c2"><path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 0 1-2.063-2.065 2.064 2.064 0 1 1 2.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/></svg>
            LinkedIn
          </span>` : ''}
        </div>
        <div class="profile-stats">
          <div class="profile-stat">
            <div class="profile-stat-num">${connectionsCount}</div>
            <div class="profile-stat-label">Connections</div>
          </div>
          <div class="profile-stat">
            <div class="profile-stat-num">${followersCount}</div>
            <div class="profile-stat-label">Followers</div>
          </div>
          <div class="profile-stat">
            <div class="profile-stat-num">${followingCount}</div>
            <div class="profile-stat-label">Following</div>
          </div>
        </div>
      </div>

      ${!isProfileComplete ? `
      <div class="profile-complete-banner anim-fade-up">
        <p><strong>Complete your profile</strong>Add your photo, role, and bio so other builders can discover you.</p>
        <button class="btn btn-primary btn-sm" id="btn-complete-profile">Edit Profile</button>
      </div>` : ''}

      <div class="profile-section anim-fade-up" style="animation-delay:0.1s;">
        <div class="profile-section-title">📊 Networking Dashboard</div>
        <div class="dashboard-grid">
          <div class="dashboard-card">
            <div class="dashboard-card-value">${connectionsCount}</div>
            <div class="dashboard-card-label">Total Connected</div>
          </div>
          <div class="dashboard-card">
            <div class="dashboard-card-value">${followersCount}</div>
            <div class="dashboard-card-label">Followers</div>
          </div>
          <div class="dashboard-card">
            <div class="dashboard-card-value">${followingCount}</div>
            <div class="dashboard-card-label">Following</div>
          </div>
          <div class="dashboard-card">
            <div class="dashboard-card-value">${tags.length}</div>
            <div class="dashboard-card-label">Interest Tags</div>
          </div>
        </div>
      </div>

      <div class="profile-section anim-fade-up" style="animation-delay:0.15s;">
        <div class="profile-section-title">🏷️ Interest Tags</div>
        <div class="profile-tags-list">
          ${tags.length > 0
            ? tags.map((t) => `<span class="tag">${escapeHtml(t)}</span>`).join('')
            : '<span style="color:var(--text-muted);font-size:0.85rem;">No tags listed yet. Add them in Edit Profile.</span>'}
        </div>
      </div>

      <div class="profile-section anim-fade-up" style="animation-delay:0.2s;">
        <div class="profile-menu-item" data-action="edit">
          <div class="profile-menu-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          </div>
          <span class="profile-menu-label">Edit Profile</span>
          <svg class="profile-menu-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </div>
        <div class="profile-menu-item" data-action="settings">
          <div class="profile-menu-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
          </div>
          <span class="profile-menu-label">Settings</span>
          <svg class="profile-menu-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </div>
        <div class="profile-menu-item" data-action="help">
          <div class="profile-menu-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          </div>
          <span class="profile-menu-label">Help & Support</span>
          <svg class="profile-menu-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </div>
      </div>

      <div class="profile-section anim-fade-up" style="animation-delay:0.25s;">
        <div class="profile-menu-item" data-action="logout" style="color:var(--red);">
          <div class="profile-menu-icon" style="background:rgba(248,113,113,0.1);">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--red)" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
          </div>
          <span class="profile-menu-label">Sign Out</span>
        </div>
      </div>

      <div style="text-align:center;padding:1rem;font-size:0.72rem;color:var(--text-muted);">
        Cosmos v1.0.0 · Made with ♥ for builders
      </div>
    </div>
  `;

  outlet.querySelector('#btn-complete-profile')?.addEventListener('click', () => {
    router.navigate('/edit-profile');
  });

  outlet.querySelectorAll('.profile-menu-item').forEach((item) => {
    item.addEventListener('click', async () => {
      const action = item.dataset.action;
      switch (action) {
        case 'logout':
          await signOut(auth);
          showToast('Signed out successfully', 'success');
          router.navigate('/auth');
          break;
        case 'edit':
          router.navigate('/edit-profile');
          break;
        case 'settings':
          router.navigate('/settings');
          break;
        case 'help':
          router.navigate('/help-support');
          break;
      }
    });
  });
}
