/* ============================================================
   Cosmos PWA — Edit Profile Page
   ============================================================ */

import {
  auth, db, storage, doc, getDoc, setDoc, updateDoc, serverTimestamp,
  ref, uploadBytes, getDownloadURL, updateAuthProfile
} from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

const USER_TYPES = [
  'Founder', 'Co-Founder', 'Startup Operator', 'Investor', 'Student',
  'Mentor', 'Tech Professional', 'Marketing Professional', 'Finance Professional',
  'Legal Professional', 'Healthcare Professional', 'Business Professional',
  'Creator', 'Freelancer', 'Service Provider', 'Community Member'
];

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

function isDefaultHeadline(headline, userTypes) {
  const trimmed = (headline || '').trim();
  if (!trimmed) return true;
  return userTypes.some((type) => {
    const lower = type.toLowerCase();
    return trimmed.toLowerCase() === lower || trimmed.toLowerCase().startsWith(`${lower} at`);
  });
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

  outlet.innerHTML = `
    <div class="edit-profile-page page">
      ${subPageHeader('Edit Profile')}
      <form class="edit-profile-form" id="edit-profile-form">
        <div class="edit-profile-avatar-wrap">
          <label class="edit-profile-avatar" for="avatar-input" id="avatar-preview">
            ${state.avatarUrl
              ? `<img src="${state.avatarUrl}" alt="Profile photo" />`
              : `<span class="edit-profile-avatar-placeholder">📷<br><small>Add Photo</small></span>`}
          </label>
          <input type="file" id="avatar-input" accept="image/*" hidden />
          <button type="button" class="btn btn-ghost btn-sm" id="btn-remove-photo" ${state.avatarUrl ? '' : 'hidden'}>Remove Photo</button>
        </div>

        <div class="settings-card edit-profile-card">
          <div class="linkedin-row">
            <div class="linkedin-icon ${state.isLinkedInConnected ? 'connected' : ''}">${state.isLinkedInConnected ? '✓' : 'in'}</div>
            <div class="linkedin-copy">
              <div class="linkedin-title">${state.isLinkedInConnected ? 'LinkedIn Connected' : 'Connect LinkedIn'}</div>
              <div class="linkedin-sub">${state.isLinkedInConnected ? 'Credentials linked & imported' : 'Import profile & build trust'}</div>
            </div>
            <button type="button" class="btn btn-outline btn-sm" id="btn-linkedin-toggle">
              ${state.isLinkedInConnected ? 'Disconnect' : 'Connect'}
            </button>
          </div>
        </div>

        <div class="form-section-label">I am a...</div>
        <div class="user-type-chips" id="user-type-chips">
          ${USER_TYPES.map((type) => `
            <button type="button" class="tag-chip ${state.primaryUserType === type ? 'active' : ''}" data-type="${type}">${type}</button>
          `).join('')}
        </div>

        <div class="form-group">
          <label class="form-label" for="field-name">Full Name *</label>
          <input class="form-input" type="text" id="field-name" value="${escapeAttr(state.name)}" placeholder="Alexandra Chen" required />
        </div>
        <div class="form-group">
          <label class="form-label" for="field-headline">Professional Headline</label>
          <input class="form-input" type="text" id="field-headline" value="${escapeAttr(state.headline)}" placeholder="Founder & CEO at NexusAI" />
        </div>
        <div class="form-group">
          <label class="form-label" for="field-role">Current Role</label>
          <input class="form-input" type="text" id="field-role" value="${escapeAttr(state.role)}" placeholder="CEO" />
        </div>
        <div class="form-group">
          <label class="form-label" for="field-company">Company</label>
          <input class="form-input" type="text" id="field-company" value="${escapeAttr(state.company)}" placeholder="NexusAI" />
        </div>
        <div class="form-group">
          <label class="form-label" for="field-location">Location</label>
          <input class="form-input" type="text" id="field-location" value="${escapeAttr(state.location)}" placeholder="San Francisco, CA" />
        </div>
        <div class="form-group">
          <label class="form-label" for="field-bio">Bio</label>
          <textarea class="form-input" id="field-bio" rows="4" placeholder="Tell other members about yourself...">${escapeHtml(state.bio)}</textarea>
        </div>

        <p class="form-error hidden" id="form-error"></p>

        <div class="edit-profile-actions">
          <button type="submit" class="btn btn-primary btn-full btn-lg" id="btn-save-profile">Save Changes</button>
        </div>
      </form>
    </div>
  `;

  const avatarInput = outlet.querySelector('#avatar-input');
  const avatarPreview = outlet.querySelector('#avatar-preview');
  const removePhotoBtn = outlet.querySelector('#btn-remove-photo');
  const formError = outlet.querySelector('#form-error');

  outlet.querySelector('#btn-sub-back').addEventListener('click', () => {
    if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
    router.navigate(backRoute);
  });

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
    removePhotoBtn.hidden = false;
  });

  removePhotoBtn.addEventListener('click', () => {
    state.pendingFile = null;
    state.avatarUrl = '';
    if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
    state.previewUrl = null;
    avatarInput.value = '';
    avatarPreview.innerHTML = `<span class="edit-profile-avatar-placeholder">📷<br><small>Add Photo</small></span>`;
    removePhotoBtn.hidden = true;
  });

  outlet.querySelector('#btn-linkedin-toggle').addEventListener('click', () => {
    if (state.isLinkedInConnected) {
      if (!confirm('Disconnect LinkedIn? This removes your verified credentials and trust badge.')) return;
      state.isLinkedInConnected = false;
    } else {
      state.isLinkedInConnected = true;
      const nameEl = outlet.querySelector('#field-name');
      const headlineEl = outlet.querySelector('#field-headline');
      const roleEl = outlet.querySelector('#field-role');
      const companyEl = outlet.querySelector('#field-company');
      const locationEl = outlet.querySelector('#field-location');
      if (!nameEl.value.trim()) nameEl.value = 'Alexandra Chen';
      if (!headlineEl.value.trim()) headlineEl.value = 'Founder & CEO at NexusAI';
      if (!roleEl.value.trim()) roleEl.value = 'CEO';
      if (!companyEl.value.trim()) companyEl.value = 'NexusAI';
      if (!locationEl.value.trim()) locationEl.value = 'San Francisco, CA';
      if (!state.primaryUserType) selectUserType('Founder');
    }
    refreshLinkedInUI(outlet, state);
  });

  outlet.querySelectorAll('.tag-chip').forEach((chip) => {
    chip.addEventListener('click', () => {
      selectUserType(chip.dataset.type, outlet);
    });
  });

  const companyEl = outlet.querySelector('#field-company');
  companyEl.addEventListener('input', () => {
    const headlineEl = outlet.querySelector('#field-headline');
    const company = companyEl.value.trim();
    if (isDefaultHeadline(headlineEl.value, USER_TYPES)) {
      headlineEl.value = company ? `${state.primaryUserType || 'Founder'} at ${company}` : (state.primaryUserType || '');
    }
  });

  outlet.querySelector('#edit-profile-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    formError.classList.add('hidden');

    const name = outlet.querySelector('#field-name').value.trim();
    const headline = outlet.querySelector('#field-headline').value.trim();
    const role = outlet.querySelector('#field-role').value.trim();
    const company = outlet.querySelector('#field-company').value.trim();
    const location = outlet.querySelector('#field-location').value.trim();
    const bio = outlet.querySelector('#field-bio').value.trim();
    const primaryUserType = state.primaryUserType;

    if (!name) {
      formError.textContent = 'Please enter your full name.';
      formError.classList.remove('hidden');
      return;
    }
    if (!primaryUserType) {
      formError.textContent = 'Please select what type of member you are.';
      formError.classList.remove('hidden');
      return;
    }

    const saveBtn = outlet.querySelector('#btn-save-profile');
    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving...';

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

      showToast('Profile updated successfully!', 'success');
      if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
      router.navigate('/settings');
    } catch (err) {
      console.error('Profile save failed:', err);
      formError.textContent = err.message || 'Failed to save profile. Please try again.';
      formError.classList.remove('hidden');
      saveBtn.disabled = false;
      saveBtn.textContent = 'Save Changes';
    }
  });

  function selectUserType(type, root = outlet) {
    state.primaryUserType = type;
    root.querySelectorAll('.tag-chip').forEach((chip) => {
      chip.classList.toggle('active', chip.dataset.type === type);
    });

    const headlineEl = root.querySelector('#field-headline');
    const company = root.querySelector('#field-company').value.trim();
    if (isDefaultHeadline(headlineEl.value, USER_TYPES)) {
      headlineEl.value = company ? `${type} at ${company}` : type;
    }
  }
}

function refreshLinkedInUI(outlet, state) {
  const row = outlet.querySelector('.linkedin-row');
  const icon = row.querySelector('.linkedin-icon');
  const title = row.querySelector('.linkedin-title');
  const sub = row.querySelector('.linkedin-sub');
  const btn = outlet.querySelector('#btn-linkedin-toggle');

  icon.classList.toggle('connected', state.isLinkedInConnected);
  icon.textContent = state.isLinkedInConnected ? '✓' : 'in';
  title.textContent = state.isLinkedInConnected ? 'LinkedIn Connected' : 'Connect LinkedIn';
  sub.textContent = state.isLinkedInConnected ? 'Credentials linked & imported' : 'Import profile & build trust';
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
