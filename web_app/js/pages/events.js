/* ============================================================
   Cosmos PWA — Events Page
   ============================================================ */

import { auth, db, collection, onSnapshot, doc, getDoc, setDoc, updateDoc, increment, serverTimestamp } from '../firebase-config.js';
import { showToast } from '../app.js';

const tabs = ['All Events', 'Speed Networking', 'Curated Meetup', 'Invite Only', 'Industry Round'];
let activeTab = 'All Events';
let unsubEvents = null;
const registrationsMap = new Map();
const hostProfileMap = new Map();
let eventsList = [];

export async function renderEvents(outlet) {
  const user = auth.currentUser;
  if (!user) return;

  if (unsubEvents) {
    unsubEvents();
    unsubEvents = null;
  }

  outlet.innerHTML = `
    <div class="events-page page animate-fade-in">
      <div class="page-header" style="margin-bottom: 1.5rem;">
        <div>
          <h1 class="page-title">Events</h1>
          <p class="page-subtitle">Structured networking, real connections</p>
        </div>
      </div>
      
      <div class="events-tabs" id="events-tabs" style="margin-bottom: 1.5rem;">
        ${tabs.map(t => `
          <button class="event-tab ${t === activeTab ? 'active' : ''}" data-tab="${t}">${t}</button>
        `).join('')}
      </div>

      <!-- Your Events Section -->
      <div class="your-events-section" id="your-events-section">
        <h3 class="your-events-title">Your Events</h3>
        <div class="your-events-container" id="your-events-container">
          <div class="loading-spinner" style="margin:1rem auto; display:block;"></div>
        </div>
      </div>

      <!-- Picked for You / Timeline Section -->
      <div class="explore-events-section">
        <h3 class="upcoming-events-title">Picked for You</h3>
        <div class="events-timeline stagger" id="events-timeline">
          <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
        </div>
      </div>
    </div>
  `;

  // Tab clicks
  outlet.querySelectorAll('.event-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      activeTab = tab.dataset.tab;
      outlet.querySelectorAll('.event-tab').forEach(t => t.classList.toggle('active', t === tab));
      updateEventsDisplay(outlet, user.uid);
    });
  });

  triggerEventsFetch(outlet, user.uid);
}

function parseEventDate(dateStr) {
  if (!dateStr) return null;
  const cleanDate = dateStr
    .replace(/(Today|Tomorrow|Next|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s*/gi, '')
    .trim();
  const d = new Date(cleanDate);
  return isNaN(d.getTime()) ? null : d;
}

function groupEventsByDay(events) {
  const groups = {};
  events.forEach(event => {
    const d = parseEventDate(event.date);
    if (!d) return;

    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const key = `${year}-${month}-${day}`;

    if (!groups[key]) {
      groups[key] = {
        date: d,
        events: []
      };
    }
    groups[key].events.push(event);
  });

  return Object.keys(groups)
    .sort()
    .map(key => groups[key]);
}

function getDayHeaderLabel(d) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);

  const eventDate = new Date(d);
  eventDate.setHours(0, 0, 0, 0);

  const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  if (eventDate.getTime() === today.getTime()) {
    return `Today / ${days[d.getDay()]}`;
  } else if (eventDate.getTime() === tomorrow.getTime()) {
    return `Tomorrow / ${days[d.getDay()]}`;
  } else {
    return `${months[d.getMonth()]} ${d.getDate()} / ${days[d.getDay()]}`;
  }
}

function updateEventsDisplay(outlet, currentUserId) {
  const yourEventsContainer = outlet.querySelector('#your-events-container');
  const timeline = outlet.querySelector('#events-timeline');
  if (!yourEventsContainer || !timeline) return;

  const filtered = getFilteredEvents();

  // 1. Render Your Events
  const registered = eventsList.filter(e => registrationsMap.get(e.id) === true);
  if (registered.length === 0) {
    yourEventsContainer.innerHTML = `
      <div class="your-events-empty-card">
        <div class="your-events-empty-icon">🎟️</div>
        <div class="your-events-empty-info">
          <h4>No Upcoming Events</h4>
          <p>Events you are going to or hosting will show up here.</p>
        </div>
      </div>
    `;
  } else {
    yourEventsContainer.innerHTML = renderLumaEventCards(registered);
  }

  // 2. Render Upcoming Timeline (grouped by day)
  if (filtered.length === 0) {
    timeline.innerHTML = `
      <div class="empty-state">
        <div class="empty-state-icon">📅</div>
        <h3 class="empty-state-title">No Events Found</h3>
        <p class="empty-state-desc">No upcoming events in this category. Check back soon!</p>
      </div>
    `;
  } else {
    const dayGroups = groupEventsByDay(filtered);
    timeline.innerHTML = dayGroups.map(group => {
      const label = getDayHeaderLabel(group.date);
      return `
        <div class="timeline-day-group">
          <div class="timeline-day-header">${label}</div>
          <div class="timeline-day-list">
            ${renderLumaEventCards(group.events)}
          </div>
        </div>
      `;
    }).join('');
  }

  // Resolve creators names and avatars asynchronously
  resolveHostProfiles(outlet);

  // Attach card listeners
  attachEventCardListeners(outlet, currentUserId);
}

function getFilteredEvents() {
  if (activeTab === 'All Events') return eventsList;
  const tabMap = {
    'Speed Networking': 'SPEED_NETWORKING',
    'Curated Meetup': 'CURATED_MEETUP',
    'Invite Only': 'INVITE_ONLY',
    'Industry Round': 'INDUSTRY_ROUND'
  };
  const typeKey = tabMap[activeTab] || activeTab;
  return eventsList.filter(e => e.type === typeKey);
}

function triggerEventsFetch(outlet, currentUserId) {
  if (unsubEvents) {
    unsubEvents();
  }

  unsubEvents = onSnapshot(collection(db, 'events'), async (snapshot) => {
    eventsList = [];
    const checkRegPromises = [];

    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      const eventId = docSnap.id;
      const dateStr = data.date || '';

      const d = parseEventDate(dateStr);
      if (d) {
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        if (d < today) return;
      }

      // Parallel check if current user is registered
      const promise = getDoc(doc(db, 'events', eventId, 'registrants', currentUserId))
        .then(regSnap => {
          registrationsMap.set(eventId, regSnap.exists());
        })
        .catch(err => {
          console.error('[Cosmos Events] Registration check failed:', eventId, err);
          registrationsMap.set(eventId, false);
        });

      checkRegPromises.push(promise);

      eventsList.push({
        id: eventId,
        title: data.title || 'Unnamed Event',
        description: data.description || '',
        date: data.date || '',
        time: data.time || '',
        location: data.location || '',
        type: data.type || 'OPEN_NETWORKING',
        participantCount: data.participantCount || 0,
        maxParticipants: data.maxParticipants || 100,
        isPaid: data.isPaid || false,
        price: data.price || '',
        coverUrl: data.coverUrl || '',
        tags: data.tags || [],
        createdBy: data.createdBy || '',
        createdAt: data.createdAt
      });
    });

    await Promise.all(checkRegPromises);
    
    // Sort events list chronologically
    eventsList.sort((a, b) => {
      const da = parseEventDate(a.date) || new Date(8640000000000000);
      const dbDate = parseEventDate(b.date) || new Date(8640000000000000);
      return da - dbDate;
    });

    updateEventsDisplay(outlet, currentUserId);
  }, (error) => {
    console.error('[Cosmos Events] Error listening to events:', error);
    const timeline = outlet.querySelector('#events-timeline');
    if (timeline) {
      timeline.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load events: ${error.message}</div>`;
    }
  });
}

function renderLumaEventCards(events) {
  return events.map(event => {
    const isJoined = registrationsMap.get(event.id) || false;
    const initial = event.title.charAt(0);

    return `
      <div class="luma-event-card anim-fade-up" data-id="${event.id}">
        <div class="luma-card-left">
          <div class="luma-card-cover" style="${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? `background: ${getGradientCss(event.coverUrl)};` : ''}">
            ${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? '📅' : `<img src="${event.coverUrl}" alt="${event.title}" loading="lazy" />`}
          </div>
        </div>
        <div class="luma-card-right">
          <div class="event-host-badge" data-creator-id="${event.createdBy}">
            <div class="host-avatar-placeholder"><div class="host-avatar-initial">${initial}</div></div>
            <span class="host-name">Cosmos Member</span>
          </div>
          <h4 class="luma-card-title">${event.title}</h4>
          <div class="luma-card-meta">
            <svg class="luma-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            <span class="luma-time-text">${event.time}</span>
          </div>
          <div class="luma-card-meta">
            <svg class="luma-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
            <span class="luma-location-text">${event.location}</span>
          </div>
        </div>
      </div>
    `;
  }).join('');
}

async function fetchHostProfile(uid) {
  if (hostProfileMap.has(uid)) return hostProfileMap.get(uid);
  try {
    const userSnap = await getDoc(doc(db, 'users', uid));
    if (userSnap.exists()) {
      const data = userSnap.data();
      const profile = { name: data.name || 'Cosmos Member', avatarUrl: data.avatarUrl || '' };
      hostProfileMap.set(uid, profile);
      return profile;
    }
  } catch (e) {
    console.warn('[Cosmos Events] Host fetch error:', uid, e);
  }
  const fallback = { name: 'Cosmos Member', avatarUrl: '' };
  hostProfileMap.set(uid, fallback);
  return fallback;
}

function resolveHostProfiles(outlet) {
  const hostBadges = outlet.querySelectorAll('.event-host-badge');
  hostBadges.forEach(async badge => {
    const creatorId = badge.dataset.creatorId;
    if (!creatorId) return;
    const profile = await fetchHostProfile(creatorId);

    const avatarPlaceholder = badge.querySelector('.host-avatar-placeholder');
    const nameEl = badge.querySelector('.host-name');

    if (avatarPlaceholder && nameEl) {
      if (profile.avatarUrl) {
        avatarPlaceholder.innerHTML = `<img src="${profile.avatarUrl}" class="host-avatar" alt="${profile.name}" />`;
      } else {
        avatarPlaceholder.innerHTML = `<div class="host-avatar-initial">${profile.name.charAt(0)}</div>`;
      }
      nameEl.textContent = profile.name;
    }
  });
}

function attachEventCardListeners(outlet, currentUserId) {
  outlet.querySelectorAll('.luma-event-card').forEach(card => {
    card.addEventListener('click', () => {
      const eventId = card.dataset.id;
      const event = eventsList.find(ev => ev.id === eventId);
      if (event) showEventDetailsModal(outlet, event, currentUserId);
    });
  });
}

function showEventDetailsModal(outlet, event, currentUserId) {
  let modal = document.getElementById('event-details-modal');
  if (!modal) {
    modal = document.createElement('div');
    modal.className = 'modal-overlay hidden';
    modal.id = 'event-details-modal';
    document.body.appendChild(modal);
  }
  const spotsLeft = event.maxParticipants - event.participantCount;
  const isJoined = registrationsMap.get(event.id) || false;

  modal.innerHTML = `
    <div class="modal-card" style="max-width:440px; position:relative; overflow:hidden;">
      <button class="modal-close" id="btn-close-details-modal" style="position:absolute; right:12px; top:12px; border:none; background:rgba(0,0,0,0.5); border-radius:50%; width:28px; height:28px; display:flex; align-items:center; justify-content:center; color:white; cursor:pointer; z-index:10;">✕</button>
      
      <div class="event-card-cover" style="height: 160px; margin: -1.5rem -1.5rem 1.25rem -1.5rem; overflow: hidden; ${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? `background: ${getGradientCss(event.coverUrl)};` : ''}">
        ${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? '' : `<img src="${event.coverUrl}" style="width: 100%; height: 100%; object-fit: cover;" alt="${event.title}" />`}
      </div>
      
      <div style="display:flex; flex-direction:column; gap:0.5rem; margin-bottom:1.25rem;">
        <div class="event-host-badge" data-creator-id="${event.createdBy}">
          <div class="host-avatar-placeholder"><div class="host-avatar-initial">${event.title.charAt(0)}</div></div>
          <span class="host-name">Cosmos Member</span>
        </div>
        <h3 style="font-family:var(--font-display); font-size:1.25rem; font-weight:800; color:white; margin:0;">${event.title}</h3>
        
        <div class="luma-card-meta" style="margin-top:0.25rem;">
          <svg class="luma-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
          <span>${event.date}</span>
        </div>
        <div class="luma-card-meta">
          <svg class="luma-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          <span class="luma-time-text">${event.time}</span>
        </div>
        <div class="luma-card-meta">
          <svg class="luma-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
          <span>${event.location}</span>
        </div>
        <div class="luma-card-meta">
          <svg class="luma-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
          <span>${spotsLeft > 0 ? `${spotsLeft} spots left` : 'Sold out'} (${event.participantCount} going)</span>
        </div>
      </div>
      
      <div style="max-height: 180px; overflow-y: auto; margin-bottom: 1.5rem; padding-right: 4px;">
        <p style="font-size:0.88rem; color:var(--text-secondary); line-height:1.6; margin:0; white-space:pre-wrap;">${event.description}</p>
      </div>
      
      <button class="btn ${isJoined ? 'btn-success' : 'btn-primary'} event-join-btn-modal" style="width:100%; py:10px; font-weight:700;" data-event-id="${event.id}" ${isJoined || spotsLeft <= 0 ? 'disabled' : ''}>
        ${isJoined ? 'Joined! ✓' : (spotsLeft <= 0 ? 'Full' : 'Join Event')}
      </button>
    </div>
  `;

  resolveHostProfiles(modal);
  modal.classList.remove('hidden');
  modal.querySelector('#btn-close-details-modal').onclick = () => modal.classList.add('hidden');

  const joinBtn = modal.querySelector('.event-join-btn-modal');
  if (joinBtn && !isJoined && spotsLeft > 0) {
    joinBtn.addEventListener('click', async () => {
      joinBtn.disabled = true;
      joinBtn.textContent = 'Joining...';
      try {
        await setDoc(doc(db, 'events', event.id, 'registrants', currentUserId), { registeredAt: serverTimestamp() });
        await updateDoc(doc(db, 'events', event.id), { participantCount: increment(1) });
        registrationsMap.set(event.id, true);
        showToast(`Joined ${event.title}!`, 'success');
        modal.classList.add('hidden');
        updateEventsDisplay(outlet, currentUserId);
      } catch (err) {
        showToast('Failed to join', 'error');
        joinBtn.disabled = false;
        joinBtn.textContent = 'Join Event';
      }
    });
  }
}

function getGradientCss(coverUrl) {
  const gradient = coverUrl || 'gradient:cosmos-glow';
  switch (gradient) {
    case 'gradient:cosmos-glow': return 'linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #581c87 100%)';
    case 'gradient:sunset-aurora': return 'linear-gradient(135deg, #1e1b4b 0%, #701a75 50%, #f43f5e 100%)';
    case 'gradient:cyber-neon': return 'linear-gradient(135deg, #020617 0%, #0f766e 60%, #06b6d4 100%)';
    case 'gradient:deep-space': return 'linear-gradient(135deg, #030712 0%, #1e1b4b 40%, #db2777 100%)';
    case 'gradient:emerald-matrix': return 'linear-gradient(135deg, #022c22 0%, #065f46 50%, #10b981 100%)';
    default: return 'linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #581c87 100%)';
  }
}
