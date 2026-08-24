/* ============================================================
   Cosmos PWA — Edit Profile Page (Cosmic Edition)
   ============================================================ */

import {
  auth, db, storage, doc, getDoc, setDoc, updateDoc, serverTimestamp,
  ref, uploadBytes, getDownloadURL, updateAuthProfile
} from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

const USER_TYPES = [
  { label: 'Founder', icon: '🚀' },
  { label: 'Co-Founder', icon: '🛸' },
  { label: 'Startup Operator', icon: '🪐' },
  { label: 'Investor', icon: '💎' },
  { label: 'Student', icon: '🎓' },
  { label: 'Mentor', icon: '🧭' },
  { label: 'Tech Professional', icon: '⚡' },
  { label: 'Marketing Professional', icon: '📣' },
  { label: 'Finance Professional', icon: '📈' },
  { label: 'Legal Professional', icon: '⚖️' },
  { label: 'Healthcare Professional', icon: '🧬' },
  { label: 'Business Professional', icon: '💼' },
  { label: 'Creator', icon: '🎨' },
  { label: 'Freelancer', icon: '🔮' },
  { label: 'Service Provider', icon: '🛠️' },
  { label: 'Community Member', icon: '🌌' }
];

const USER_TYPE_LABELS = USER_TYPES.map(t => t.label);

function subPageHeader(title, backRoute) {
  return `
    <div class="sub-page-header">
      <button class="btn-back" id="btn-sub-back" aria-label="Go back">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
      </button>
      <h1 class="page-title">${title}</h1>
    </div>
  `;
}

function isDefaultHeadline(headline, userTypeLabels) {
  const trimmed = (headline || '').trim();
  if (!trimmed) return true;
  return userTypeLabels.some((type) => {
    const lower = type.toLowerCase();
    return trimmed.toLowerCase() === lower || trimmed.toLowerCase().startsWith(`${lower} at`);
  });
}

function getPersonaIcon(userType) {
  const found = USER_TYPES.find(t => t.label === userType);
  return found ? found.icon : '✨';
}

function getInitials(name) {
  if (!name) return 'C';
  return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2) || 'C';
}

export async function renderEditProfile(outlet, path = '') {
  const user = auth.currentUser;
  if (!user) {
    router.navigate('/auth');
    return;
  }

  const backRoute = router.getBackRoute('/settings');

  let profile = {};
  try {
    const snap = await getDoc(doc(db, 'users', user.uid));
    if (snap.exists()) profile = snap.data();
  } catch (e) {
    console.error('Failed to load profile:', e);
  }

  const state = {
    name: profile.name || user.displayName || '',
    headline: profile.headline || '',
    role: profile.role || '',
    company: profile.company || '',
    location: profile.location || '',
    bio: profile.bio || '',
    primaryUserType: profile.primaryUserType || '',
    avatarUrl: profile.avatarUrl || user.photoURL || '',
    isLinkedInConnected: profile.isLinkedInConnected || false,
    pendingFile: null,
    previewUrl: null,
  };

  const initialPersonaIcon = getPersonaIcon(state.primaryUserType);
  const initialInitials = getInitials(state.name || user.displayName || 'Explorer');

  outlet.innerHTML = `
    <div class="cosmic-edit-page page">
      <div class="cosmic-nebula-bg"></div>
      
      <div class="cosmic-edit-container">
        ${subPageHeader('Cosmic Profile Forge')}

        <div style="display:flex;justify-content:center;">
          <div class="cosmic-badge-pill">
            <span class="cosmic-badge-dot"></span>
            Cosmos Citizen Identity Forge
          </div>
        </div>

        <!-- ── Real-time Hologram Profile Preview ── -->
        <div class="cosmic-preview-card" id="cosmic-live-card">
          <div class="cosmic-preview-header">
            <span class="cosmic-preview-label">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>
              Live Hologram Card
            </span>
            <span class="cosmic-live-indicator" title="Live Preview Synchronized"></span>
          </div>
          <div class="cosmic-preview-content">
            <div class="cosmic-preview-avatar" id="live-card-avatar">
              ${state.avatarUrl
                ? `<img src="${escapeAttr(state.avatarUrl)}" alt="Avatar" />`
                : `<div class="avatar-fallback">${initialInitials}</div>`}
            </div>
            <div class="cosmic-preview-details">
              <div class="cosmic-preview-name" id="live-card-name">
                <span>${escapeHtml(state.name || 'Your Name')}</span>
                ${state.isLinkedInConnected ? `<svg width="15" height="15" viewBox="0 0 24 24" fill="#0a66c2"><path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 0 1-2.063-2.065 2.064 2.064 0 1 1 2.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/></svg>` : ''}
              </div>
              <div class="cosmic-preview-headline" id="live-card-headline">
                ${escapeHtml(state.headline || (state.company ? `${state.primaryUserType || 'Builder'} at ${state.company}` : state.primaryUserType || 'Cosmos Citizen'))}
              </div>
              <div class="cosmic-preview-meta" id="live-card-meta">
                <span class="cosmic-meta-pill" id="live-card-persona">${initialPersonaIcon} ${escapeHtml(state.primaryUserType || 'Explorer')}</span>
                ${state.location ? `<span class="cosmic-meta-pill" id="live-card-location">📍 ${escapeHtml(state.location)}</span>` : ''}
                ${state.company ? `<span class="cosmic-meta-pill" id="live-card-company">🏢 ${escapeHtml(state.company)}</span>` : ''}
              </div>
            </div>
          </div>
        </div>

        <form class="edit-profile-form" id="edit-profile-form">
          <!-- ── Avatar Forge Orb ── -->
          <div class="cosmic-avatar-forge">
            <label class="cosmic-avatar-ring" for="avatar-input" id="avatar-ring-label" title="Tap to upload cosmic avatar">
              <div class="cosmic-avatar-inner" id="avatar-preview">
                ${state.avatarUrl
                  ? `<img src="${escapeAttr(state.avatarUrl)}" alt="Profile photo" />`
                  : `<div class="cosmic-avatar-placeholder">
                      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
                      <span>Upload Avatar</span>
                     </div>`}
              </div>
              <div class="cosmic-avatar-edit-badge" title="Change photo">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
              </div>
            </label>
            <input type="file" id="avatar-input" accept="image/*" hidden />
            
            <div class="cosmic-avatar-actions">
              <button type="button" class="btn btn-ghost btn-sm" id="btn-remove-photo" ${state.avatarUrl ? '' : 'hidden'} style="font-size:0.8rem;color:var(--red);">
                ✕ Remove Avatar
              </button>
            </div>
          </div>

          <!-- ── LinkedIn Trust Node ── -->
          <div class="cosmic-linkedin-card">
            <div class="cosmic-linkedin-left">
              <div class="cosmic-linkedin-icon ${state.isLinkedInConnected ? 'connected' : ''}">
                ${state.isLinkedInConnected ? '✓' : 'in'}
              </div>
              <div class="cosmic-linkedin-info">
                <h4 class="linkedin-title">${state.isLinkedInConnected ? 'LinkedIn Verified & Synced' : 'Connect LinkedIn Node'}</h4>
                <p class="linkedin-sub">${state.isLinkedInConnected ? 'Credentials linked & trust beacon active' : 'Import cosmic credentials & build peer trust'}</p>
              </div>
            </div>
            <button type="button" class="btn btn-outline btn-sm" id="btn-linkedin-toggle">
              ${state.isLinkedInConnected ? 'Disconnect' : 'Connect'}
            </button>
          </div>

          <!-- ── Section 1: Cosmic Identity ── -->
          <div class="cosmic-section-card">
            <div class="cosmic-section-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--purple)" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="m4.93 4.93 4.24 4.24"/><path d="m14.83 9.17 4.24-4.24"/><path d="m14.83 14.83 4.24 4.24"/><path d="m9.17 14.83-4.24 4.24"/></svg>
              Cosmic Identity
            </div>
            <div class="cosmic-section-sub">Select your primary role archetype in the Cosmos ecosystem.</div>

            <div class="cosmic-persona-grid" id="user-type-chips">
              ${USER_TYPES.map((type) => `
                <button type="button" class="cosmic-chip ${state.primaryUserType === type.label ? 'active' : ''}" data-type="${type.label}" data-icon="${type.icon}">
                  <span>${type.icon}</span>
                  <span>${type.label}</span>
                </button>
              `).join('')}
            </div>

            <div style="margin-top: 1.25rem;">
              <div class="cosmic-field-group">
                <label class="cosmic-field-label" for="field-name">
                  <span>Full Legal / Display Name <span class="required-star">*</span></span>
                </label>
                <div class="cosmic-input-box">
                  <span class="cosmic-input-icon">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                  </span>
                  <input class="cosmic-input" type="text" id="field-name" value="${escapeAttr(state.name)}" placeholder="Alexandra Chen" required />
                </div>
              </div>
            </div>
          </div>

          <!-- ── Section 2: Orbital Trajectory & Work ── -->
          <div class="cosmic-section-card">
            <div class="cosmic-section-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--blue)" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
              Orbital Trajectory
            </div>
            <div class="cosmic-section-sub">Your professional orbit, ventures, and mission statement.</div>

            <div class="cosmic-field-group">
              <label class="cosmic-field-label" for="field-headline">Professional Headline</label>
              <div class="cosmic-input-box">
                <span class="cosmic-input-icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z"/><path d="m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z"/><path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0"/><path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5"/></svg>
                </span>
                <input class="cosmic-input" type="text" id="field-headline" value="${escapeAttr(state.headline)}" placeholder="Founder & CEO at NexusAI" />
              </div>
            </div>

            <div style="display:grid;grid-template-columns:1fr 1fr;gap:0.85rem;">
              <div class="cosmic-field-group">
                <label class="cosmic-field-label" for="field-role">Current Role</label>
                <div class="cosmic-input-box">
                  <span class="cosmic-input-icon">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>
                  </span>
                  <input class="cosmic-input" type="text" id="field-role" value="${escapeAttr(state.role)}" placeholder="CEO / Lead Architect" />
                </div>
              </div>

              <div class="cosmic-field-group">
                <label class="cosmic-field-label" for="field-company">Company / Venture</label>
                <div class="cosmic-input-box">
                  <span class="cosmic-input-icon">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18Z"/><path d="M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2"/><path d="M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2"/><path d="M10 6h4"/><path d="M10 10h4"/><path d="M10 14h4"/><path d="M10 18h4"/></svg>
                  </span>
                  <input class="cosmic-input" type="text" id="field-company" value="${escapeAttr(state.company)}" placeholder="NexusAI" />
                </div>
              </div>
            </div>
          </div>

          <!-- ── Section 3: Planetary Coordinates & Transmission ── -->
          <div class="cosmic-section-card">
            <div class="cosmic-section-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--teal)" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
              Planetary Coordinates & Transmission
            </div>
            <div class="cosmic-section-sub">Where on Earth you operate and your message to the Cosmos community.</div>

            <div class="cosmic-field-group">
              <label class="cosmic-field-label" for="field-location">Location / Base</label>
              <div class="cosmic-input-box">
                <span class="cosmic-input-icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"/></svg>
                </span>
                <input class="cosmic-input" type="text" id="field-location" value="${escapeAttr(state.location)}" placeholder="San Francisco, CA (or Remote)" />
              </div>
            </div>

            <div class="cosmic-field-group">
              <label class="cosmic-field-label" for="field-bio">
                <span>Cosmic Bio / Transmission</span>
                <span class="cosmic-char-counter" id="bio-char-count">${(state.bio || '').length} / 500</span>
              </label>
              <div class="cosmic-input-box" style="align-items:flex-start;">
                <textarea class="cosmic-input cosmic-textarea" id="field-bio" maxlength="500" placeholder="Tell fellow founders, creators, and operators what you are building, what inspires you, and how you can collaborate...">${escapeHtml(state.bio)}</textarea>
              </div>
            </div>
          </div>

          <p class="form-error hidden" id="form-error"></p>

          <div class="cosmic-save-wrap">
            <button type="submit" class="btn-cosmic-save" id="btn-save-profile">
              <span class="cosmic-save-sparkle">✨</span>
              <span>Save Cosmic Profile</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  `;

  // DOM Elements
  const avatarInput = outlet.querySelector('#avatar-input');
  const avatarPreview = outlet.querySelector('#avatar-preview');
  const removePhotoBtn = outlet.querySelector('#btn-remove-photo');
  const formError = outlet.querySelector('#form-error');

  const liveCardAvatar = outlet.querySelector('#live-card-avatar');
  const liveCardName = outlet.querySelector('#live-card-name span');
  const liveCardHeadline = outlet.querySelector('#live-card-headline');
  const liveCardPersona = outlet.querySelector('#live-card-persona');
  const liveCardMeta = outlet.querySelector('#live-card-meta');

  const nameInput = outlet.querySelector('#field-name');
  const headlineInput = outlet.querySelector('#field-headline');
  const roleInput = outlet.querySelector('#field-role');
  const companyInput = outlet.querySelector('#field-company');
  const locationInput = outlet.querySelector('#field-location');
  const bioInput = outlet.querySelector('#field-bio');
  const bioCharCount = outlet.querySelector('#bio-char-count');

  // Back Navigation
  outlet.querySelector('#btn-sub-back').addEventListener('click', () => {
    if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
    router.navigate(backRoute);
  });

  // Dynamic Live Card Updater
  function updateLiveCard() {
    const currentName = nameInput.value.trim() || 'Your Name';
    liveCardName.textContent = currentName;

    const currentType = state.primaryUserType || 'Explorer';
    const personaIcon = getPersonaIcon(currentType);
    liveCardPersona.textContent = `${personaIcon} ${currentType}`;

    const currentCompany = companyInput.value.trim();
    const currentHeadline = headlineInput.value.trim() || (currentCompany ? `${currentType} at ${currentCompany}` : currentType);
    liveCardHeadline.textContent = currentHeadline;

    const currentLocation = locationInput.value.trim();
    
    // Reconstruct meta pills
    let metaHTML = `<span class="cosmic-meta-pill" id="live-card-persona">${personaIcon} ${escapeHtml(currentType)}</span>`;
    if (currentLocation) {
      metaHTML += `<span class="cosmic-meta-pill">📍 ${escapeHtml(currentLocation)}</span>`;
    }
    if (currentCompany) {
      metaHTML += `<span class="cosmic-meta-pill">🏢 ${escapeHtml(currentCompany)}</span>`;
    }
    liveCardMeta.innerHTML = metaHTML;

    // Update avatar in live card if fallback
    if (!state.previewUrl && !state.avatarUrl) {
      liveCardAvatar.innerHTML = `<div class="avatar-fallback">${getInitials(currentName)}</div>`;
    }
  }

  // Reactive inputs
  nameInput.addEventListener('input', updateLiveCard);
  headlineInput.addEventListener('input', updateLiveCard);
  locationInput.addEventListener('input', updateLiveCard);

  companyInput.addEventListener('input', () => {
    const headlineEl = headlineInput;
    const company = companyInput.value.trim();
    if (isDefaultHeadline(headlineEl.value, USER_TYPE_LABELS)) {
      headlineEl.value = company ? `${state.primaryUserType || 'Founder'} at ${company}` : (state.primaryUserType || '');
    }
    updateLiveCard();
  });

  bioInput.addEventListener('input', () => {
    bioCharCount.textContent = `${bioInput.value.length} / 500`;
  });

  // Avatar Upload & Handling
  avatarInput.addEventListener('change', () => {
    const file = avatarInput.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      showToast('Please choose an image file.', 'error');
      avatarInput.value = '';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      showToast('Image must be smaller than 5 MB.', 'error');
      avatarInput.value = '';
      return;
    }
    if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
    state.pendingFile = file;
    state.previewUrl = URL.createObjectURL(file);

    avatarPreview.innerHTML = `<img src="${state.previewUrl}" alt="Selected photo" />`;
    liveCardAvatar.innerHTML = `<img src="${state.previewUrl}" alt="Selected photo" />`;
    removePhotoBtn.hidden = false;
  });

  removePhotoBtn.addEventListener('click', () => {
    state.pendingFile = null;
    state.avatarUrl = '';
    if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
    state.previewUrl = null;
    avatarInput.value = '';
    avatarPreview.innerHTML = `
      <div class="cosmic-avatar-placeholder">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
        <span>Upload Avatar</span>
      </div>`;
    liveCardAvatar.innerHTML = `<div class="avatar-fallback">${getInitials(nameInput.value || 'Explorer')}</div>`;
    removePhotoBtn.hidden = true;
  });

  // LinkedIn Toggle
  outlet.querySelector('#btn-linkedin-toggle').addEventListener('click', async () => {
    if (state.isLinkedInConnected) {
      if (!confirm('Disconnect LinkedIn? This removes your verified credentials and trust badge.')) return;
      try {
        state.isLinkedInConnected = false;
        await updateDoc(doc(db, 'users', user.uid), {
          isLinkedInConnected: false,
          linkedInProfile: null,
          updatedAt: serverTimestamp()
        });
        refreshLinkedInUI(outlet, state);
        updateLiveCard();
        showToast('LinkedIn disconnected', 'success');
      } catch (err) {
        showToast('Failed to disconnect LinkedIn', 'error');
      }
    } else {
      const clientId = '86w9zd45y9pupv';
      let redirectUri = window.location.origin + window.location.pathname;
      if (redirectUri.endsWith('/')) {
        redirectUri = redirectUri.slice(0, -1);
      }
      const oauthState = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);

      sessionStorage.setItem('linkedin_oauth_state', oauthState);
      sessionStorage.setItem('linkedin_redirect_route', '/edit-profile');

      const authUrl = `https://www.linkedin.com/oauth/v2/authorization?response_type=code&client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&state=${oauthState}&scope=openid%20profile%20email`;
      
      showToast('Redirecting to LinkedIn...', 'info');
      setTimeout(() => {
        window.location.href = authUrl;
      }, 800);
    }
  });

  // Cosmic Persona Selection
  outlet.querySelectorAll('.cosmic-chip').forEach((chip) => {
    chip.addEventListener('click', () => {
      selectUserType(chip.dataset.type, outlet);
    });
  });

  function selectUserType(type, root = outlet) {
    state.primaryUserType = type;
    root.querySelectorAll('.cosmic-chip').forEach((chip) => {
      chip.classList.toggle('active', chip.dataset.type === type);
    });

    const headlineEl = root.querySelector('#field-headline');
    const company = root.querySelector('#field-company').value.trim();
    if (isDefaultHeadline(headlineEl.value, USER_TYPE_LABELS)) {
      headlineEl.value = company ? `${type} at ${company}` : type;
    }
    updateLiveCard();
  }

  // Form Submit
  outlet.querySelector('#edit-profile-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    formError.classList.add('hidden');

    const name = nameInput.value.trim();
    const headline = headlineInput.value.trim();
    const role = roleInput.value.trim();
    const company = companyInput.value.trim();
    const location = locationInput.value.trim();
    const bio = bioInput.value.trim();
    const primaryUserType = state.primaryUserType;

    if (!name) {
      formError.textContent = 'Please enter your full name.';
      formError.classList.remove('hidden');
      return;
    }
    if (!primaryUserType) {
      formError.textContent = 'Please select what type of cosmic member you are.';
      formError.classList.remove('hidden');
      return;
    }

    const saveBtn = outlet.querySelector('#btn-save-profile');
    saveBtn.disabled = true;
    saveBtn.innerHTML = `
      <span class="spinner" style="width:16px;height:16px;border-width:2px;display:inline-block;margin-right:8px;"></span>
      <span>Broadcasting Profile to Cosmos...</span>
    `;

    try {
      let avatarUrl = state.avatarUrl;
      if (state.pendingFile) {
        const storageRef = ref(storage, `avatars/${user.uid}.jpg`);
        await uploadBytes(storageRef, state.pendingFile);
        avatarUrl = await getDownloadURL(storageRef);
      }

      const resolvedHeadline = headline || (company ? `${primaryUserType} at ${company}` : primaryUserType);

      const profilePayload = {
        name,
        headline: resolvedHeadline,
        role,
        company,
        location,
        bio,
        primaryUserType,
        avatarUrl,
        isLinkedInConnected: state.isLinkedInConnected,
        isProfileComplete: true,
        updatedAt: serverTimestamp(),
      };

      await setDoc(doc(db, 'users', user.uid), profilePayload, { merge: true });

      await updateAuthProfile(user, {
        displayName: name,
        photoURL: avatarUrl || null,
      });

      window.cosmosApp.userProfile = {
        ...(window.cosmosApp.userProfile || {}),
        ...profilePayload,
        avatarUrl,
      };

      showToast('✨ Cosmic Profile updated successfully!', 'success');
      if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
      router.navigate('/settings');
    } catch (err) {
      console.error('Profile save failed:', err);
      formError.textContent = err.message || 'Failed to save profile. Please try again.';
      formError.classList.remove('hidden');
      saveBtn.disabled = false;
      saveBtn.innerHTML = `
        <span class="cosmic-save-sparkle">✨</span>
        <span>Save Cosmic Profile</span>
      `;
    }
  });
}

function refreshLinkedInUI(outlet, state) {
  const row = outlet.querySelector('.cosmic-linkedin-card');
  if (!row) return;
  const icon = row.querySelector('.cosmic-linkedin-icon');
  const title = row.querySelector('.linkedin-title');
  const sub = row.querySelector('.linkedin-sub');
  const btn = outlet.querySelector('#btn-linkedin-toggle');

  icon.classList.toggle('connected', state.isLinkedInConnected);
  icon.textContent = state.isLinkedInConnected ? '✓' : 'in';
  title.textContent = state.isLinkedInConnected ? 'LinkedIn Verified & Synced' : 'Connect LinkedIn Node';
  sub.textContent = state.isLinkedInConnected ? 'Credentials linked & trust beacon active' : 'Import cosmic credentials & build peer trust';
  btn.textContent = state.isLinkedInConnected ? 'Disconnect' : 'Connect';
}

function escapeAttr(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;');
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}
