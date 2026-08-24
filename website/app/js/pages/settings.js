/* ============================================================
   Cosmos PWA — Settings & Control Center (Android Parity)
   ============================================================ */

import {
  auth, db, doc, getDoc, updateDoc, deleteDoc,
  collection, query, where, onSnapshot, increment,
  serverTimestamp, updatePassword, reauthenticateWithCredential,
  EmailAuthProvider, addDoc, setDoc
} from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

let connUnsubscribe = null;
let incomingUnsubscribe = null;
let outgoingUnsubscribe = null;
let userUnsubscribe = null;

const mockFollowers = [];
const mockFollowing = [];
const mockConnections = [];

function escapeHtml(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

export async function renderSettings(outlet) {
  const user = auth.currentUser;
  if (!user) {
    router.navigate('/auth');
    return;
  }

  let profile = window.cosmosApp?.userProfile || {};
  try {
    const snap = await getDoc(doc(db, 'users', user.uid));
    if (snap.exists()) {
      profile = snap.data();
      window.cosmosApp.userProfile = profile;
    }
  } catch (e) {
    console.warn('Failed to fetch user profile:', e);
  }

  const displayName = profile.name || user.displayName || user.email?.split('@')[0] || 'Cosmos Builder';
  const headline = profile.headline || profile.role || 'Cosmos Pioneer';
  const company = profile.company || '';
  const location = profile.location || '';
  const photoURL = profile.avatarUrl || user.photoURL || '';
  const initials = displayName.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2) || 'U';
  const membershipTier = (profile.membershipTier || 'EXPLORER').toUpperCase();
  const isLinkedIn = !!profile.isLinkedInConnected;
  const connectionsCount = profile.connectionsCount || 0;
  const followersCount = profile.followersCount || 0;
  const followingCount = profile.followingCount || 0;
  const eventsCount = profile.eventsAttended || 0;
  const followUpsCount = profile.followUpsCompleted || 0;
  const orbitsCount = profile.joinedCirclesCount || profile.circlesCount || 0;
  const monthlyLimit = profile.monthlyConnectionLimit || 10;
  const limitText = monthlyLimit > 100 ? 'Unlimited' : `${monthlyLimit}`;
  const usedThisMonth = Math.min(connectionsCount % (monthlyLimit > 100 ? 100 : monthlyLimit), monthlyLimit);
  const progressPercent = monthlyLimit > 100 ? 100 : Math.min(Math.round((usedThisMonth / monthlyLimit) * 100), 100);

  const tierMeta = {
    EXPLORER: { label: 'Explorer', badge: '🚀', class: 'tier-badge-explorer', glow: 'rgba(96,165,250,0.2)' },
    MEMBER: { label: 'Member', badge: '✨', class: 'tier-badge-member', glow: 'rgba(52,211,153,0.2)' },
    INNER_CIRCLE: { label: 'Inner Circle', badge: '💎', class: 'tier-badge-inner', glow: 'rgba(167,139,250,0.25)' },
    FOUNDER: { label: 'Founder', badge: '👑', class: 'tier-badge-founder', glow: 'rgba(251,191,36,0.3)' },
  };
  const currentTier = tierMeta[membershipTier] || tierMeta.EXPLORER;

  outlet.innerHTML = `
    <div class="settings-page page">
      <!-- ── Floating Cosmic Particles ── -->
      <div class="cosmic-particles">
        <div class="cosmic-particle"></div>
        <div class="cosmic-particle"></div>
        <div class="cosmic-particle"></div>
        <div class="cosmic-particle"></div>
        <div class="cosmic-particle"></div>
        <div class="cosmic-particle"></div>
        <div class="cosmic-particle"></div>
        <div class="cosmic-particle"></div>
      </div>

      <!-- ── Cosmic Command Header ── -->
      <div class="settings-header-banner cosmic-enter">
        <h1 class="cosmic-command-title">Command Center</h1>
        <p class="cosmic-command-subtitle">Manage your identity, connectivity, privacy, and cosmic journey</p>
      </div>

      <!-- ── Holographic Hero Profile Card ── -->
      <div class="settings-hero-card cosmic-enter" style="animation-delay:0.06s;">
        <div class="settings-hero-bg"></div>
        <div class="settings-hero-content">
          <div class="settings-avatar-wrap">
            <div class="cosmic-avatar-orbit">
              <div class="avatar avatar-lg settings-avatar" style="${photoURL ? '' : 'background:var(--gradient-primary);'}">
                ${photoURL ? `<img src="${photoURL}" alt="${escapeHtml(displayName)}" />` : initials}
              </div>
            </div>
            ${isLinkedIn ? '<div class="settings-verified-badge" title="Verified LinkedIn">✓</div>' : ''}
          </div>
          <div class="settings-hero-info">
            <div class="settings-hero-name-row">
              <h2 class="settings-hero-name">${escapeHtml(displayName)}</h2>
              <button class="settings-tier-pill ${currentTier.class}" id="btn-open-membership" title="View Membership & Tiers">
                <span>${currentTier.badge}</span>
                <span>${currentTier.label}</span>
              </button>
            </div>
            <div class="settings-hero-headline">${escapeHtml(headline)}</div>
            <div class="settings-hero-meta">
              ${company ? `<span class="settings-meta-item">🏢 ${escapeHtml(company)}</span>` : ''}
              ${location ? `<span class="settings-meta-item">📍 ${escapeHtml(location)}</span>` : ''}
              <span class="settings-meta-item" style="color:${isLinkedIn ? 'var(--blue)' : 'var(--text-muted)'};">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.779-1.75-1.75s.784-1.75 1.75-1.75 1.75.779 1.75 1.75-.784 1.75-1.75 1.75zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z"/></svg>
                ${isLinkedIn ? 'LinkedIn Verified' : 'Not Connected'}
              </span>
            </div>
          </div>
          <div class="settings-hero-action">
            <button class="btn btn-outline btn-sm" id="btn-quick-edit" style="border-radius:12px;gap:6px;">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              Edit Profile
            </button>
          </div>
        </div>
      </div>

      <!-- ── Cosmic Fuel Gauge (Monthly Progress) ── -->
      <div class="settings-section cosmic-enter" style="animation-delay:0.1s;">
        <div class="cosmic-section-header">
          <span class="cosmic-section-emoji">⛽</span>
          <span class="cosmic-section-title">Cosmic Fuel</span>
        </div>
        <div class="cosmic-fuel-gauge">
          <div class="settings-progress-header">
            <span class="settings-progress-label">Connections this month</span>
            <span class="settings-progress-count">${usedThisMonth} of ${limitText}</span>
          </div>
          <div class="cosmic-progress-track">
            <div class="cosmic-progress-fill" style="width:${progressPercent}%;"></div>
          </div>
          <div class="settings-progress-desc">
            ${monthlyLimit > 100 ? '✨ Unlimited introductions active with your cosmic tier.' : `${Math.max(0, monthlyLimit - usedThisMonth)} more curated introductions available this billing cycle.`}
          </div>
        </div>
      </div>

      <!-- ── Orbital Stats Grid ── -->
      <div class="settings-section cosmic-enter" style="animation-delay:0.14s;">
        <div class="cosmic-section-header">
          <span class="cosmic-section-emoji">📡</span>
          <span class="cosmic-section-title">Signal Metrics</span>
        </div>
        <div class="settings-stats-grid">
          <div class="cosmic-stat-pod" style="--stat-accent:var(--purple);" id="tile-followers">
            <div class="cosmic-stat-value" id="val-followers" style="color:var(--purple);">${followersCount}</div>
            <div class="cosmic-stat-label">Followers</div>
          </div>
          <div class="cosmic-stat-pod" style="--stat-accent:var(--blue);" id="tile-following">
            <div class="cosmic-stat-value" id="val-following" style="color:var(--blue);">${followingCount}</div>
            <div class="cosmic-stat-label">Following</div>
          </div>
          <div class="cosmic-stat-pod" style="--stat-accent:var(--teal);" id="tile-connections">
            <div class="cosmic-stat-value" id="val-connections" style="color:var(--teal);">${connectionsCount}</div>
            <div class="cosmic-stat-label">Connections</div>
          </div>
          <div class="cosmic-stat-pod" style="--stat-accent:var(--amber);" id="tile-events">
            <div class="cosmic-stat-value" style="color:var(--amber);">${eventsCount}</div>
            <div class="cosmic-stat-label">Events</div>
          </div>
          <div class="cosmic-stat-pod" style="--stat-accent:var(--green);" id="tile-followups">
            <div class="cosmic-stat-value" style="color:var(--green);">${followUpsCount}</div>
            <div class="cosmic-stat-label">Follow-ups</div>
          </div>
          <div class="cosmic-stat-pod" style="--stat-accent:var(--pink);" id="tile-orbits">
            <div class="cosmic-stat-value" style="color:var(--pink);">${orbitsCount}</div>
            <div class="cosmic-stat-label">Orbits</div>
          </div>
        </div>
      </div>

      <!-- ── 🛸 Account & Identity ── -->
      <div class="settings-section cosmic-enter" style="animation-delay:0.18s;">
        <div class="cosmic-section-header">
          <span class="cosmic-section-emoji">🛸</span>
          <span class="cosmic-section-title">Account</span>
        </div>
        <div class="settings-card">
          <div class="settings-item" id="item-edit-profile">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg></div>
            <div class="settings-item-label">Edit Profile</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-change-password">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg></div>
            <div class="settings-item-label">Change Password</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-connected-accounts">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg></div>
            <div class="settings-item-label">Connected Accounts</div>
            <span class="settings-item-value">${isLinkedIn ? 'Google + LinkedIn' : 'Google'}</span>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-linkedin">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.779-1.75-1.75s.784-1.75 1.75-1.75 1.75.779 1.75 1.75-.784 1.75-1.75 1.75zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z"/></svg></div>
            <div class="settings-item-label">LinkedIn Connection</div>
            <span class="settings-item-value" id="txt-linkedin-status">${isLinkedIn ? 'Connected' : 'Not Connected'}</span>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>
      </div>

      <!-- ── 🌟 COSMOS Membership & Upgrades ── -->
      <div class="settings-section cosmic-enter" style="animation-delay:0.22s;">
        <div class="cosmic-section-header">
          <span class="cosmic-section-emoji">🌟</span>
          <span class="cosmic-section-title">Cosmos Membership</span>
        </div>
        <div class="settings-card">
          <div class="settings-item" id="item-membership-plan">
            <div class="settings-item-icon" style="background:rgba(251,191,36,0.12);color:var(--amber);box-shadow:0 0 12px rgba(251,191,36,0.1);"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg></div>
            <div class="settings-item-label">Current Plan</div>
            <span class="settings-item-value" style="color:var(--purple);font-weight:600;">${currentTier.badge} ${currentTier.label}</span>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-billing-status">
            <div class="settings-item-icon" style="background:rgba(52,211,153,0.12);color:var(--green);box-shadow:0 0 12px rgba(52,211,153,0.1);"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg></div>
            <div class="settings-item-label">Billing Status</div>
            <span class="settings-item-value">Lifetime Member</span>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-upgrade-journey">
            <div class="settings-item-icon" style="background:rgba(167,139,250,0.15);box-shadow:0 0 12px rgba(167,139,250,0.15);"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z"/><path d="M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z"/></svg></div>
            <div class="settings-item-label">Cosmic Journey & Upgrades</div>
            <span class="settings-item-value" style="color:var(--purple-l);">Explore Tiers</span>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>
      </div>

      <!-- ── 🌐 Networking & Discovery ── -->
      <div class="settings-section cosmic-enter" style="animation-delay:0.26s;">
        <div class="cosmic-section-header">
          <span class="cosmic-section-emoji">🌐</span>
          <span class="cosmic-section-title">Networking & Discovery</span>
        </div>
        <div class="settings-card">
          <div class="settings-item" id="item-network-relations">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg></div>
            <div class="settings-item-label">Network Relations & Requests</div>
            <span class="settings-item-value">View Active</span>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>

          <div class="settings-item" id="item-matching-prefs">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg></div>
            <div class="settings-item-label">Matching Preferences</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-availability-prefs">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
            <div class="settings-item-label">Availability & Scheduling</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-blocked-users">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg></div>
            <div class="settings-item-label">Blocked Users</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>
      </div>

      <!-- ── 🔔 Notifications Matrix ── -->
      <div class="settings-section cosmic-enter" style="animation-delay:0.3s;">
        <div class="cosmic-section-header">
          <span class="cosmic-section-emoji">🔔</span>
          <span class="cosmic-section-title">Notifications</span>
        </div>
        <div class="settings-card settings-card-padded">
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">New Matches</div>
              <div class="settings-toggle-desc">Notify when a mutual discovery match is made</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-matches" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Messages</div>
              <div class="settings-toggle-desc">Notify when a direct chat message is received</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-messages" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Event Invitations</div>
              <div class="settings-toggle-desc">Alerts when invited to summits or meetups</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-events" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Event Reminders</div>
              <div class="settings-toggle-desc">Upcoming event alerts 1 hour prior</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-event-reminders" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">AI Meeting Summaries</div>
              <div class="settings-toggle-desc">Instant summary and key actions after calls</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-ai" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Follow-up Reminders</div>
              <div class="settings-toggle-desc">Reminders to maintain active relationships</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-followups" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Warm Intro Requests</div>
              <div class="settings-toggle-desc">Alerts when trusted peers request introductions</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-intros" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Community Announcements</div>
              <div class="settings-toggle-desc">Updates from joined Orbits & circles</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-orbits" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Skill Endorsements</div>
              <div class="settings-toggle-desc">Notify when peers endorse your verified skills</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-notif-endorsements" checked><span class="slider round"></span></label>
          </div>
        </div>
      </div>

      <!-- ── 🛡️ Privacy & Visibility ── -->
      <div class="settings-section cosmic-enter" style="animation-delay:0.34s;">
        <div class="cosmic-section-header">
          <span class="cosmic-section-emoji">🛡️</span>
          <span class="cosmic-section-title">Privacy</span>
        </div>
        <div class="settings-card settings-card-padded">
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Profile Visibility</div>
              <div class="settings-toggle-desc">Show profile in discovery matchmaking deck</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-priv-visibility" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Show LinkedIn Connection</div>
              <div class="settings-toggle-desc">Display LinkedIn verification badge publicly</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-priv-linkedin" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Allow Warm Intro Requests</div>
              <div class="settings-toggle-desc">Enable 2nd-degree connections to request warm intros</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-priv-intros" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Mutual Connections</div>
              <div class="settings-toggle-desc">Display shared connections to other members</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-priv-mutual" checked><span class="slider round"></span></label>
          </div>
          <div class="settings-toggle-row">
            <div class="settings-toggle-copy">
              <div class="settings-toggle-title">Data & Analytics</div>
              <div class="settings-toggle-desc">Allow anonymous networking analytics to improve matching</div>
            </div>
            <label class="switch"><input type="checkbox" id="sw-priv-analytics" checked><span class="slider round"></span></label>
          </div>
        </div>
      </div>

      <!-- ── ℹ️ Support & Information ── -->
      <div class="settings-section cosmic-enter" style="animation-delay:0.38s;">
        <div class="cosmic-section-header">
          <span class="cosmic-section-emoji">ℹ️</span>
          <span class="cosmic-section-title">Support & Info</span>
        </div>
        <div class="settings-card">
          <div class="settings-item" id="item-help-support">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg></div>
            <div class="settings-item-label">Help & Support</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" id="item-guidelines">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg></div>
            <div class="settings-item-label">Community Guidelines</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item" style="cursor:default;">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg></div>
            <div class="settings-item-label">App Version</div>
            <span class="settings-item-value" style="color:var(--text-muted);font-size:0.78rem;">Cosmos v1.0.0</span>
          </div>
        </div>
      </div>

      <!-- ── ⚠️ Danger Zone ── -->
      <div class="settings-section cosmic-enter" style="animation-delay:0.42s;margin-bottom:2rem;">
        <div class="cosmic-section-header">
          <span class="cosmic-section-emoji">⚠️</span>
          <span class="cosmic-section-title" style="background:linear-gradient(135deg, #f87171, #ef4444);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;">Danger Zone</span>
        </div>
        <div class="settings-card-danger">
          <div class="settings-item settings-item-danger" id="item-logout">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg></div>
            <div class="settings-item-label">Sign Out</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item settings-item-danger" id="item-pause-account">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="10" y1="15" x2="10" y2="9"/><line x1="14" y1="15" x2="14" y2="9"/></svg></div>
            <div class="settings-item-label">Pause Account</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="settings-item settings-item-danger" id="item-delete-account">
            <div class="settings-item-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg></div>
            <div class="settings-item-label">Delete Account</div>
            <svg class="settings-item-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>
      </div>

      <!-- ── Network Relations Modal ── -->
      <div class="relations-modal hidden" id="relations-modal">
        <div class="relations-modal-inner">
          <div style="display:flex;justify-content:space-between;align-items:center;padding:1.25rem 1rem;border-bottom:1.5px solid var(--border);">
            <h2 id="modal-title" style="margin:0;font-size:1.25rem;font-weight:700;font-family:'Outfit',sans-serif;">Network Relations</h2>
            <button id="modal-close" class="btn-icon" style="background:rgba(255,255,255,0.05);color:var(--text-primary);border-radius:50%;font-size:1.2rem;">✕</button>
          </div>
          <div class="modal-tabs" style="display:flex;padding:0.5rem;border-bottom:1px solid var(--border);background:rgba(255,255,255,0.02);">
            <button class="modal-tab" data-tab="followers" style="flex:1;background:none;border:none;color:var(--text-muted);font-weight:600;padding:0.75rem 0;font-size:0.88rem;cursor:pointer;border-radius:10px;">Followers</button>
            <button class="modal-tab" data-tab="following" style="flex:1;background:none;border:none;color:var(--text-muted);font-weight:600;padding:0.75rem 0;font-size:0.88rem;cursor:pointer;border-radius:10px;">Following</button>
            <button class="modal-tab" data-tab="connections" style="flex:1;background:none;border:none;color:var(--text-muted);font-weight:600;padding:0.75rem 0;font-size:0.88rem;cursor:pointer;border-radius:10px;">Connections</button>
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


  // ── Event Handlers ──
  const attachClicks = () => {
    // Edit profile
    const editHandler = () => router.navigate('/edit-profile');
    outlet.querySelector('#btn-quick-edit')?.addEventListener('click', editHandler);
    outlet.querySelector('#item-edit-profile')?.addEventListener('click', editHandler);

    // Membership dialog
    const membershipHandler = () => showMembershipModal(membershipTier, user.uid);
    outlet.querySelector('#btn-open-membership')?.addEventListener('click', membershipHandler);
    outlet.querySelector('#item-membership-plan')?.addEventListener('click', membershipHandler);
    outlet.querySelector('#item-billing-status')?.addEventListener('click', membershipHandler);
    outlet.querySelector('#item-upgrade-journey')?.addEventListener('click', membershipHandler);

    // Password & LinkedIn
    outlet.querySelector('#item-change-password')?.addEventListener('click', () => showChangePasswordModal(user));
    outlet.querySelector('#item-linkedin')?.addEventListener('click', () => handleLinkedInToggle(outlet, user.uid));
    outlet.querySelector('#item-connected-accounts')?.addEventListener('click', () => showConnectedAccountsModal(profile, user));

    // Networking Preferences Modals
    outlet.querySelector('#item-matching-prefs')?.addEventListener('click', () => showMatchingPrefsModal(user.uid, profile));
    outlet.querySelector('#item-availability-prefs')?.addEventListener('click', () => showAvailabilityModal(user.uid, profile));
    outlet.querySelector('#item-blocked-users')?.addEventListener('click', () => showBlockedUsersModal(user.uid, profile));

    // Support
    outlet.querySelector('#item-help-support')?.addEventListener('click', () => router.navigate('/help-support'));
    outlet.querySelector('#item-guidelines')?.addEventListener('click', () => showGuidelinesModal());

    // Danger zone
    outlet.querySelector('#item-logout')?.addEventListener('click', async () => {
      await auth.signOut();
      showToast('Signed out successfully', 'success');
      router.navigate('/auth');
    });
    outlet.querySelector('#item-pause-account')?.addEventListener('click', () => showPauseAccountModal(user.uid));
    outlet.querySelector('#item-delete-account')?.addEventListener('click', () => showDeleteAccountModal(user));
  };
  attachClicks();

  // ── Setup Realtime Toggles ──
  const setupToggle = (id, field) => {
    const el = outlet.querySelector(`#${id}`);
    if (!el) return;
    el.addEventListener('change', async (e) => {
      try {
        await updateDoc(doc(db, 'users', user.uid), {
          [field]: e.target.checked,
          updatedAt: serverTimestamp(),
        });
        showToast('Preference updated', 'success');
      } catch (err) {
        e.target.checked = !e.target.checked;
        showToast('Failed to update preference', 'error');
      }
    });
  };

  // Populate checkbox states
  const setChecked = (id, val, fallback = true) => {
    const el = outlet.querySelector(`#${id}`);
    if (el) el.checked = val !== undefined ? val : fallback;
  };
  setChecked('sw-notif-matches', profile.notificationNewMatches, true);
  setChecked('sw-notif-messages', profile.notificationMessages, true);
  setChecked('sw-notif-events', profile.notificationEventInvitations, true);
  setChecked('sw-notif-event-reminders', profile.notificationEventReminders, true);
  setChecked('sw-notif-ai', profile.notificationAiSummaries, true);
  setChecked('sw-notif-followups', profile.notificationFollowUpReminders, true);
  setChecked('sw-notif-intros', profile.notificationWarmIntroRequests, true);
  setChecked('sw-notif-orbits', profile.notificationCommunityAnnouncements, true);
  setChecked('sw-notif-endorsements', profile.notificationEndorsements, true);

  setChecked('sw-priv-visibility', profile.privacyProfileVisibility, true);
  setChecked('sw-priv-linkedin', profile.privacyShowLinkedIn, true);
  setChecked('sw-priv-intros', profile.privacyAllowWarmIntros, true);
  setChecked('sw-priv-mutual', profile.privacyShowMutualConnections, true);
  setChecked('sw-priv-analytics', profile.privacyDataAnalytics, true);

  setupToggle('sw-notif-matches', 'notificationNewMatches');
  setupToggle('sw-notif-messages', 'notificationMessages');
  setupToggle('sw-notif-events', 'notificationEventInvitations');
  setupToggle('sw-notif-event-reminders', 'notificationEventReminders');
  setupToggle('sw-notif-ai', 'notificationAiSummaries');
  setupToggle('sw-notif-followups', 'notificationFollowUpReminders');
  setupToggle('sw-notif-intros', 'notificationWarmIntroRequests');
  setupToggle('sw-notif-orbits', 'notificationCommunityAnnouncements');
  setupToggle('sw-notif-endorsements', 'notificationEndorsements');

  setupToggle('sw-priv-visibility', 'privacyProfileVisibility');
  setupToggle('sw-priv-linkedin', 'privacyShowLinkedIn');
  setupToggle('sw-priv-intros', 'privacyAllowWarmIntros');
  setupToggle('sw-priv-mutual', 'privacyShowMutualConnections');
  setupToggle('sw-priv-analytics', 'privacyDataAnalytics');

  // Relations dashboard
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
    delete window.handleWebAcceptAction;
    delete window.handleWebDeclineAction;
    delete window.handleWebWithdrawAction;
  };
}

/* ============================================================
   MEMBERSHIP TIERS & COSMIC JOURNEY MODAL
   ============================================================ */
function showMembershipModal(currentTierKey, uid) {
  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  modal.innerHTML = `
    <div class="modal-card membership-modal-card">
      <div class="modal-header">
        <div style="display:flex;align-items:center;gap:8px;">
          <span style="font-size:1.4rem;">✨</span>
          <div>
            <h3 style="margin:0;">COSMOS Membership</h3>
            <p style="margin:0.2rem 0 0;font-size:0.8rem;color:var(--text-muted);">Ascend your professional networking frequency</p>
          </div>
        </div>
        <button type="button" class="modal-close" id="modal-membership-close">✕</button>
      </div>
      <div class="modal-body" style="padding:1.25rem 1rem;">
        <div class="membership-tiers-grid">
          <!-- Explorer -->
          <div class="membership-tier-card ${currentTierKey === 'EXPLORER' ? 'active-tier' : ''}">
            <div class="membership-tier-header">
              <div class="tier-icon">🚀</div>
              <div class="tier-title">Explorer</div>
              <div class="tier-price">Free <span>/ lifetime</span></div>
            </div>
            <ul class="tier-perks">
              <li>✓ 10 curated connections / month</li>
              <li>✓ AI matchmaking algorithm</li>
              <li>✓ Public Orbits participation</li>
              <li>✓ Basic meeting notes</li>
            </ul>
            ${currentTierKey === 'EXPLORER' ? '<span class="tier-badge-current">Current Active Plan</span>' : ''}
          </div>

          <!-- Member -->
          <div class="membership-tier-card ${currentTierKey === 'MEMBER' ? 'active-tier' : ''}">
            <div class="membership-tier-header">
              <div class="tier-icon">✨</div>
              <div class="tier-title">Member</div>
              <div class="tier-price">Standard <span>/ active</span></div>
            </div>
            <ul class="tier-perks">
              <li>✓ 25 curated introductions / month</li>
              <li>✓ Priority discovery deck ranking</li>
              <li>✓ AI action item extraction</li>
              <li>✓ Verified credential badge</li>
            </ul>
            ${currentTierKey === 'MEMBER' ? '<span class="tier-badge-current">Current Active Plan</span>' : '<button class="btn btn-outline btn-sm btn-full tier-upgrade-btn" data-tier="MEMBER">Select Member</button>'}
          </div>

          <!-- Inner Circle -->
          <div class="membership-tier-card ${currentTierKey === 'INNER_CIRCLE' ? 'active-tier' : ''}" style="border-color:rgba(167,139,250,0.4);">
            <div class="membership-tier-header">
              <div class="tier-icon">💎</div>
              <div class="tier-title">Inner Circle</div>
              <div class="tier-price">VIP <span>/ elite</span></div>
            </div>
            <ul class="tier-perks">
              <li>✓ 50 curated introductions / month</li>
              <li>✓ Direct warm intro facilitation</li>
              <li>✓ Private VIP Orbits creation</li>
              <li>✓ Full CRM & relationship timeline</li>
            </ul>
            ${currentTierKey === 'INNER_CIRCLE' ? '<span class="tier-badge-current">Current Active Plan</span>' : '<button class="btn btn-primary btn-sm btn-full tier-upgrade-btn" data-tier="INNER_CIRCLE">Upgrade to Inner Circle</button>'}
          </div>

          <!-- Founder -->
          <div class="membership-tier-card ${currentTierKey === 'FOUNDER' ? 'active-tier' : ''}" style="border-color:rgba(251,191,36,0.5);background:linear-gradient(180deg, rgba(251,191,36,0.06) 0%, rgba(20,24,34,0.9) 100%);">
            <div class="membership-tier-header">
              <div class="tier-icon">👑</div>
              <div class="tier-title" style="color:var(--amber);">Founder</div>
              <div class="tier-price">Cosmic <span>/ honorary</span></div>
            </div>
            <ul class="tier-perks">
              <li>✓ Unlimited curated introductions</li>
              <li>✓ Top-tier spotlight across Cosmos</li>
              <li>✓ Sovereign Community Governance</li>
              <li>✓ Dedicated Cosmic Concierge</li>
            </ul>
            ${currentTierKey === 'FOUNDER' ? '<span class="tier-badge-current">Current Active Plan</span>' : '<button class="btn btn-primary btn-sm btn-full tier-upgrade-btn" data-tier="FOUNDER" style="background:linear-gradient(135deg, #fbbf24, #f59e0b);color:#000;font-weight:700;">Ascend to Founder</button>'}
          </div>
        </div>
      </div>
    </div>
  `;

  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-membership-close').addEventListener('click', close);

  modal.querySelectorAll('.tier-upgrade-btn').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const selectedTier = btn.dataset.tier;
      try {
        btn.disabled = true;
        btn.textContent = 'Updating...';
        await updateDoc(doc(db, 'users', uid), {
          membershipTier: selectedTier,
          updatedAt: serverTimestamp()
        });
        showToast(`Upgraded to ${selectedTier.replace('_', ' ')}! 🎉`, 'success');
        close();
        router.navigate('/settings');
      } catch (err) {
        showToast('Failed to update tier', 'error');
        btn.disabled = false;
      }
    });
  });

  document.body.appendChild(modal);
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   CHANGE PASSWORD MODAL
   ============================================================ */
function showChangePasswordModal(user) {
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

  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
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
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   CONNECTED ACCOUNTS MODAL
   ============================================================ */
function showConnectedAccountsModal(profile, user) {
  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <h3>Connected Accounts</h3>
        <button type="button" class="modal-close" id="modal-conn-close">✕</button>
      </div>
      <div class="modal-body">
        <div style="display:flex;flex-direction:column;gap:1rem;">
          <div class="settings-item" style="border:1px solid var(--border);border-radius:12px;cursor:default;">
            <div class="settings-item-icon" style="color:var(--blue);"><svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg></div>
            <div class="settings-item-label">Google Account</div>
            <span class="badge badge-green">Connected</span>
          </div>
          <div class="settings-item" style="border:1px solid var(--border);border-radius:12px;cursor:default;">
            <div class="settings-item-icon" style="color:#0a66c2;"><svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.779-1.75-1.75s.784-1.75 1.75-1.75 1.75.779 1.75 1.75-.784 1.75-1.75 1.75zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z"/></svg></div>
            <div class="settings-item-label">LinkedIn Profile</div>
            <span class="badge ${profile.isLinkedInConnected ? 'badge-green' : 'badge-amber'}">${profile.isLinkedInConnected ? 'Connected' : 'Not Connected'}</span>
          </div>
        </div>
      </div>
    </div>
  `;
  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-conn-close').addEventListener('click', close);
  document.body.appendChild(modal);
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   MONTHLY CONNECTION LIMIT MODAL
   ============================================================ */
function showMonthlyLimitModal(uid, currentLimit, outlet) {
  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <h3>Monthly Connection Limit</h3>
        <button type="button" class="modal-close" id="modal-limit-close">✕</button>
      </div>
      <div class="modal-body">
        <p style="font-size:0.88rem;color:var(--text-secondary);margin-bottom:1rem;">Select your maximum desired introductions per month to balance quality networking with your schedule.</p>
        <div style="display:flex;flex-direction:column;gap:0.75rem;">
          <label class="settings-item" style="border:1px solid var(--border);border-radius:12px;cursor:pointer;">
            <input type="radio" name="limit-choice" value="10" ${currentLimit === 10 ? 'checked' : ''} style="margin-right:8px;" />
            <div class="settings-item-label">10 Introductions / mo (Standard)</div>
          </label>
          <label class="settings-item" style="border:1px solid var(--border);border-radius:12px;cursor:pointer;">
            <input type="radio" name="limit-choice" value="25" ${currentLimit === 25 ? 'checked' : ''} style="margin-right:8px;" />
            <div class="settings-item-label">25 Introductions / mo (Active Builder)</div>
          </label>
          <label class="settings-item" style="border:1px solid var(--border);border-radius:12px;cursor:pointer;">
            <input type="radio" name="limit-choice" value="50" ${currentLimit === 50 ? 'checked' : ''} style="margin-right:8px;" />
            <div class="settings-item-label">50 Introductions / mo (Growth Focus)</div>
          </label>
          <label class="settings-item" style="border:1px solid var(--border);border-radius:12px;cursor:pointer;">
            <input type="radio" name="limit-choice" value="999" ${currentLimit > 100 ? 'checked' : ''} style="margin-right:8px;" />
            <div class="settings-item-label">Unlimited Introductions (Max Frequency)</div>
          </label>
        </div>
        <button class="btn btn-primary btn-full" id="btn-save-limit" style="margin-top:1.25rem;">Save Limit</button>
      </div>
    </div>
  `;
  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-limit-close').addEventListener('click', close);

  modal.querySelector('#btn-save-limit').addEventListener('click', async () => {
    const selected = parseInt(modal.querySelector('input[name="limit-choice"]:checked')?.value || '10', 10);
    try {
      await updateDoc(doc(db, 'users', uid), {
        monthlyConnectionLimit: selected,
        updatedAt: serverTimestamp()
      });
      const txt = selected > 100 ? 'Unlimited' : `${selected}`;
      outlet.querySelector('#txt-limit-val').textContent = txt;
      showToast('Connection limit saved', 'success');
      close();
    } catch (e) {
      showToast('Failed to update limit', 'error');
    }
  });

  document.body.appendChild(modal);
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   MATCHING PREFERENCES MODAL
   ============================================================ */
function showMatchingPrefsModal(uid, profile) {
  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  const currentIndustries = profile.targetIndustries || ['AI & ML', 'Web3', 'B2B SaaS'];
  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <h3>Matching Preferences</h3>
        <button type="button" class="modal-close" id="modal-match-close">✕</button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label">Discovery Mode</label>
          <select class="form-input" id="match-mode">
            <option value="CURATED" selected>AI Curated (High Alignment)</option>
            <option value="EXPEDITION">Expedition (Broad Exploration)</option>
            <option value="LOCAL">Local First (Geographic Proximity)</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Target Roles</label>
          <input class="form-input" id="match-roles" value="${escapeHtml(profile.targetRoles || 'Founders, Investors, Tech Leads')}" placeholder="e.g. Founders, CTOs, Angels" />
        </div>
        <div class="form-group">
          <label class="form-label">Target Focus Areas</label>
          <input class="form-input" id="match-focus" value="${escapeHtml(currentIndustries.join(', '))}" placeholder="e.g. AI, Climate, Fintech" />
        </div>
        <button class="btn btn-primary btn-full" id="btn-save-match">Save Preferences</button>
      </div>
    </div>
  `;
  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-match-close').addEventListener('click', close);

  modal.querySelector('#btn-save-match').addEventListener('click', async () => {
    const roles = modal.querySelector('#match-roles').value;
    const focus = modal.querySelector('#match-focus').value.split(',').map(s => s.trim()).filter(Boolean);
    const mode = modal.querySelector('#match-mode').value;
    try {
      await updateDoc(doc(db, 'users', uid), {
        targetRoles: roles,
        targetIndustries: focus,
        matchingMode: mode,
        updatedAt: serverTimestamp()
      });
      showToast('Matching preferences saved', 'success');
      close();
    } catch (e) {
      showToast('Failed to save preferences', 'error');
    }
  });

  document.body.appendChild(modal);
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   AVAILABILITY & SCHEDULING MODAL
   ============================================================ */
function showAvailabilityModal(uid, profile) {
  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <h3>Availability & Scheduling</h3>
        <button type="button" class="modal-close" id="modal-avail-close">✕</button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label">Meeting Format Preference</label>
          <select class="form-input" id="avail-format">
            <option value="15MIN">15-Minute Cosmic Coffee</option>
            <option value="30MIN" selected>30-Minute Intro Call</option>
            <option value="ASYNC">Async Messages First</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Calendar / Booking URL (Optional)</label>
          <input class="form-input" id="avail-cal-url" value="${escapeHtml(profile.calendarUrl || '')}" placeholder="https://cal.com/your-name" />
        </div>
        <div class="form-group">
          <label class="form-label">Availability Notes</label>
          <textarea class="form-input" id="avail-notes" placeholder="e.g. Available Mon/Thu afternoons PST.">${escapeHtml(profile.availabilityPreferences || '')}</textarea>
        </div>
        <button class="btn btn-primary btn-full" id="btn-save-avail">Save Availability</button>
      </div>
    </div>
  `;
  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-avail-close').addEventListener('click', close);

  modal.querySelector('#btn-save-avail').addEventListener('click', async () => {
    const format = modal.querySelector('#avail-format').value;
    const url = modal.querySelector('#avail-cal-url').value.trim();
    const notes = modal.querySelector('#avail-notes').value.trim();
    try {
      await updateDoc(doc(db, 'users', uid), {
        preferredMeetingFormat: format,
        calendarUrl: url,
        availabilityPreferences: notes,
        updatedAt: serverTimestamp()
      });
      showToast('Availability preferences saved', 'success');
      close();
    } catch (e) {
      showToast('Failed to save availability', 'error');
    }
  });

  document.body.appendChild(modal);
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   BLOCKED USERS MODAL
   ============================================================ */
function showBlockedUsersModal(uid, profile) {
  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  const blocked = profile.blockedUsers || [];
  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <h3>Blocked Users</h3>
        <button type="button" class="modal-close" id="modal-block-close">✕</button>
      </div>
      <div class="modal-body">
        ${blocked.length === 0 ? `
          <div style="text-align:center;padding:2rem 0;color:var(--text-muted);">
            <div style="font-size:2rem;margin-bottom:0.5rem;">🛡️</div>
            <div>Your blocked users list is clean.</div>
          </div>
        ` : `
          <div style="display:flex;flex-direction:column;gap:0.75rem;">
            ${blocked.map(bId => `
              <div style="display:flex;align-items:center;justify-content:space-between;padding:0.75rem;border:1px solid var(--border);border-radius:12px;">
                <span>User ID: ${escapeHtml(bId)}</span>
                <button class="btn btn-outline-danger btn-sm" onclick="unblockUser('${bId}')">Unblock</button>
              </div>
            `).join('')}
          </div>
        `}
      </div>
    </div>
  `;
  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-block-close').addEventListener('click', close);
  document.body.appendChild(modal);
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   PAUSE ACCOUNT MODAL
   ============================================================ */
function showPauseAccountModal(uid) {
  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <h3 style="color:var(--amber);">Pause Account</h3>
        <button type="button" class="modal-close" id="modal-pause-close">✕</button>
      </div>
      <div class="modal-body">
        <p style="font-size:0.9rem;color:var(--text-secondary);line-height:1.6;">
          Pausing your account hides your profile from discovery matching and incoming intro requests. You can unpause anytime by logging back in.
        </p>
        <button class="btn btn-primary btn-full" id="btn-confirm-pause" style="background:var(--amber);color:#000;font-weight:700;margin-top:1rem;">Pause My Profile</button>
      </div>
    </div>
  `;
  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-pause-close').addEventListener('click', close);

  modal.querySelector('#btn-confirm-pause').addEventListener('click', async () => {
    try {
      await updateDoc(doc(db, 'users', uid), {
        privacyProfileVisibility: false,
        isPaused: true,
        updatedAt: serverTimestamp()
      });
      showToast('Account paused. You are now invisible in discovery.', 'info');
      close();
    } catch (e) {
      showToast('Failed to pause account', 'error');
    }
  });

  document.body.appendChild(modal);
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   DELETE ACCOUNT MODAL
   ============================================================ */
function showDeleteAccountModal(user) {
  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <h3 style="color:var(--red);">Delete Account</h3>
        <button type="button" class="modal-close" id="modal-del-close">✕</button>
      </div>
      <div class="modal-body">
        <p style="font-size:0.9rem;color:var(--text-secondary);line-height:1.6;">
          ⚠️ <strong>This action is permanent and irreversible.</strong> Your Cosmos profile, circles, direct messages, connections, and badges will be deleted.
        </p>
        <button class="btn btn-danger btn-full" id="btn-confirm-delete" style="margin-top:1.25rem;">Permanently Delete Account</button>
      </div>
    </div>
  `;
  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-del-close').addEventListener('click', close);

  modal.querySelector('#btn-confirm-delete').addEventListener('click', async () => {
    if (!confirm('Are you absolutely certain you want to delete your Cosmos account?')) return;
    try {
      await deleteDoc(doc(db, 'users', user.uid));
      await user.delete();
      showToast('Account deleted', 'info');
      close();
      router.navigate('/auth');
    } catch (err) {
      console.error('Delete account failed:', err);
      showToast('Please re-login to verify your credentials before account deletion.', 'error');
    }
  });

  document.body.appendChild(modal);
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   COMMUNITY GUIDELINES MODAL
   ============================================================ */
function showGuidelinesModal() {
  const modal = document.createElement('div');
  modal.className = 'modal-overlay';
  modal.innerHTML = `
    <div class="modal-card">
      <div class="modal-header">
        <h3>Cosmos Community Guidelines</h3>
        <button type="button" class="modal-close" id="modal-guide-close">✕</button>
      </div>
      <div class="modal-body" style="line-height:1.6;font-size:0.9rem;color:var(--text-secondary);">
        <p><strong>1. Authentic Connection:</strong> Engage with respect, curiosity, and high intent. No spam or unsolicited promotions.</p>
        <p><strong>2. Mutual Respect:</strong> Foster welcoming conversations for creators, builders, founders, and leaders.</p>
        <p><strong>3. Trust & Confidentiality:</strong> Private introductions and meeting notes remain within your trusted network.</p>
        <p><strong>4. Constructive Collaboration:</strong> Support your fellow members through honest endorsements and genuine introductions.</p>
      </div>
    </div>
  `;
  const close = () => {
    modal.classList.remove('active');
    setTimeout(() => modal.remove(), 250);
  };
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  modal.querySelector('#modal-guide-close').addEventListener('click', close);
  document.body.appendChild(modal);
  modal.offsetHeight;
  modal.classList.add('active');
}

/* ============================================================
   LINKEDIN INTEGRATION TOGGLE
   ============================================================ */
async function handleLinkedInToggle(outlet, uid) {
  try {
    const userRef = doc(db, 'users', uid);
    const snap = await getDoc(userRef);
    const connected = snap.exists() && snap.data().isLinkedInConnected;

    if (connected) {
      if (!confirm('Disconnect LinkedIn? This removes your verified credentials and trust badge.')) return;
      await updateDoc(userRef, {
        isLinkedInConnected: false,
        linkedInProfile: null,
        updatedAt: serverTimestamp()
      });
      const txt = outlet.querySelector('#txt-linkedin-status');
      if (txt) txt.textContent = 'Not Connected';
      showToast('LinkedIn disconnected', 'success');
      return;
    }

    const clientId = '86w9zd45y9pupv';
    let redirectUri = window.location.origin + window.location.pathname;
    if (redirectUri.endsWith('/')) {
      redirectUri = redirectUri.slice(0, -1);
    }
    const state = Math.random().toString(36).substring(2, 15);

    sessionStorage.setItem('linkedin_oauth_state', state);
    sessionStorage.setItem('linkedin_redirect_route', '/settings');

    const authUrl = `https://www.linkedin.com/oauth/v2/authorization?response_type=code&client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&state=${state}&scope=openid%20profile%20email`;
    
    showToast('Connecting to LinkedIn...', 'info');
    setTimeout(() => {
      window.location.href = authUrl;
    }, 600);

  } catch (err) {
    showToast('Failed to update LinkedIn status', 'error');
  }
}

/* ============================================================
   NETWORK RELATIONS REALTIME LISTENERS
   ============================================================ */
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
      activeTile.style.transform = 'scale(1.04)';
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

  tileFollowers?.addEventListener('click', () => openModal('followers'));
  tileFollowing?.addEventListener('click', () => openModal('following'));
  tileConnections?.addEventListener('click', () => openModal('connections'));
  modalClose?.addEventListener('click', closeModal);

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

  window.handleWebAcceptAction = async (requestId, senderId, senderName) => {
    try {
      await updateDoc(doc(db, 'connection_requests', requestId), { status: 'ACCEPTED' });

      const connectionId = uid < senderId ? `${uid}_${senderId}` : `${senderId}_${uid}`;
      await setDoc(doc(db, 'connections', connectionId), {
        id: connectionId,
        members: [uid, senderId],
        lastMessage: 'Connection established! Say hello.',
        lastMessageTime: serverTimestamp(),
        unreadCountMap: { [uid]: 0, [senderId]: 0 },
        labels: { [uid]: [], [senderId]: [] },
        privateGoals: { [uid]: '', [senderId]: '' },
        status: 'ACTIVE',
        createdAt: serverTimestamp()
      });

      await updateDoc(doc(db, 'users', uid), {
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
        actionId: uid
      });

      showToast(`Connected with ${senderName}! 🎉`, 'success');
    } catch (err) {
      console.error('Accept request failed:', err);
      showToast('Failed to accept request', 'error');
    }
  };

  window.handleWebDeclineAction = async (requestId) => {
    try {
      await updateDoc(doc(db, 'connection_requests', requestId), { status: 'DECLINED' });
      showToast('Request declined', 'info');
    } catch (err) {
      console.error('Decline request failed:', err);
      showToast('Failed to decline request', 'error');
    }
  };

  window.handleWebWithdrawAction = async (requestId) => {
    try {
      await updateDoc(doc(db, 'connection_requests', requestId), { status: 'WITHDRAWN' });
      showToast('Request withdrawn', 'info');
    } catch (err) {
      console.error('Withdraw request failed:', err);
      showToast('Failed to withdraw request', 'error');
    }
  };

  const startListeners = () => {
    isFetching = true;
    hasFailed = false;
    relationsError?.classList.add('hidden');
    relationsList?.classList.remove('hidden');
    renderList();

    try {
      const connQuery = query(collection(db, 'connections'), where('members', 'array-contains', uid));
      connUnsubscribe = onSnapshot(connQuery, async (snapshot) => {
        const listPromises = snapshot.docs.map(async (d) => {
          const data = d.data();
          const otherId = data.members.find((m) => m !== uid) || '';
          let userProfile = { id: otherId, name: `Member ${otherId}`, headline: '', avatarUrl: '' };
          try {
            const userSnap = await getDoc(doc(db, 'users', otherId));
            if (userSnap.exists()) {
              const uData = userSnap.data();
              userProfile = {
                id: otherId,
                name: uData.name || userProfile.name,
                headline: uData.headline || '',
                avatarUrl: uData.avatarUrl || '',
                isLinkedInConnected: uData.isLinkedInConnected || false,
              };
            }
          } catch (e) {}
          return { id: d.id, member: userProfile, status: data.status };
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
          const linkedInTxt = outlet.querySelector('#txt-linkedin-status');
          if (linkedInTxt) linkedInTxt.textContent = data.isLinkedInConnected ? 'Connected' : 'Not Connected';
        }
      });
    } catch (e) {
      triggerErrorState();
    }
  };

  const triggerErrorState = () => {
    isFetching = false;
    hasFailed = true;
    relationsList?.classList.add('hidden');
    relationsError?.classList.remove('hidden');
  };

  btnRetry?.addEventListener('click', () => {
    if (connUnsubscribe) connUnsubscribe();
    if (incomingUnsubscribe) incomingUnsubscribe();
    if (outgoingUnsubscribe) outgoingUnsubscribe();
    if (userUnsubscribe) userUnsubscribe();
    startListeners();
  });

  const updateCountsDisplay = () => {
    const fConns = connectionsList.map((c) => ({ ...c.member, isConnection: true }));
    const fReqs = incomingRequests.map((r) => ({
      id: r.senderId,
      name: r.senderName,
      headline: r.senderHeadline,
      avatarUrl: r.senderAvatarUrl,
      requestId: r.id,
      isRequest: true
    }));
    const followers = (connectionsList.length > 0 || incomingRequests.length > 0) ? [...fConns, ...fReqs] : mockFollowers;
    const finalFollowers = followers.filter((f) => !removedUserIds.has(f.id));

    const fgConns = connectionsList.map((c) => ({ ...c.member, isConnection: true }));
    const fgReqs = outgoingRequests.map((r) => ({
      id: r.receiverId,
      name: r.receiverName,
      headline: r.receiverHeadline,
      avatarUrl: r.receiverAvatarUrl,
      requestId: r.id,
      isRequest: true
    }));
    const following = (connectionsList.length > 0 || outgoingRequests.length > 0) ? [...fgConns, ...fgReqs] : mockFollowing;
    const finalFollowing = following.filter((f) => !removedUserIds.has(f.id));

    const conns = connectionsList.length > 0 ? connectionsList.map((c) => c.member) : mockConnections;
    const finalConnections = conns.filter((c) => !removedUserIds.has(c.id));

    const vf = outlet.querySelector('#val-followers');
    const vfg = outlet.querySelector('#val-following');
    const vc = outlet.querySelector('#val-connections');
    if (vf) vf.textContent = finalFollowers.length;
    if (vfg) vfg.textContent = finalFollowing.length;
    if (vc) vc.textContent = finalConnections.length;
  };

  const renderList = () => {
    if (hasFailed || !relationsList) return;

    if (isFetching) {
      relationsList.innerHTML = Array(3).fill(0).map(() => `
        <div class="skeleton-row" style="display:flex;align-items:center;gap:1rem;padding:0.5rem 0;">
          <div class="skeleton" style="width:48px;height:48px;border-radius:50%;"></div>
          <div style="flex:1;display:flex;flex-direction:column;gap:0.4rem;">
            <div class="skeleton" style="width:120px;height:14px;border-radius:4px;"></div>
            <div class="skeleton" style="width:80px;height:10px;border-radius:3px;"></div>
          </div>
          <div class="skeleton" style="width:80px;height:32px;border-radius:16px;"></div>
        </div>
      `).join('');
      return;
    }

    let list = [];
    if (activeTab === 'followers') {
      const fConns = connectionsList.map((c) => ({ ...c.member, isConnection: true }));
      const fReqs = incomingRequests.map((r) => ({
        id: r.senderId,
        name: r.senderName,
        headline: r.senderHeadline,
        avatarUrl: r.senderAvatarUrl,
        requestId: r.id,
        isRequest: true
      }));
      list = (connectionsList.length > 0 || incomingRequests.length > 0) ? [...fConns, ...fReqs] : mockFollowers;
    } else if (activeTab === 'following') {
      const fgConns = connectionsList.map((c) => ({ ...c.member, isConnection: true }));
      const fgReqs = outgoingRequests.map((r) => ({
        id: r.receiverId,
        name: r.receiverName,
        headline: r.receiverHeadline,
        avatarUrl: r.receiverAvatarUrl,
        requestId: r.id,
        isRequest: true
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
        if (item.isRequest) {
          btnHtml = `
            <div style="display:flex;gap:4px;">
              <button class="btn btn-primary btn-sm" onclick="handleWebAcceptAction('${item.requestId}', '${item.id}', '${item.name.replace(/'/g, "\\'")}')" style="border-radius:18px;padding:0.35rem 0.75rem;font-size:0.75rem;">Accept</button>
              <button class="btn btn-outline-danger btn-sm" onclick="handleWebDeclineAction('${item.requestId}')" style="border-radius:18px;padding:0.35rem 0.75rem;font-size:0.75rem;">Decline</button>
            </div>
          `;
        } else {
          btnHtml = `<button class="btn btn-outline-danger btn-sm" onclick="handleWebRemoveAction('${item.id}', 'followers')" style="border-radius:18px;padding:0.35rem 1rem;font-size:0.78rem;">Remove</button>`;
        }
      } else if (activeTab === 'following') {
        if (item.isRequest) {
          btnHtml = `
            <button class="btn btn-outline btn-sm" onclick="handleWebWithdrawAction('${item.requestId}')" style="border-radius:18px;padding:0.35rem 1rem;font-size:0.78rem;color:var(--text-muted);border-color:var(--border);">Withdraw</button>
          `;
        } else {
          btnHtml = `
            <button class="btn btn-sm" onclick="handleWebRemoveAction('${item.id}', 'following')" style="border-radius:18px;padding:0.35rem 1rem;font-size:0.78rem;background:rgba(255,255,255,0.08);color:var(--text-primary);border:1px solid rgba(255,255,255,0.15);display:flex;align-items:center;gap:4px;">
              <span style="color:var(--purple);">✓</span> Following
            </button>
          `;
        }
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
