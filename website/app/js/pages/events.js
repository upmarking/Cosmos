/* ============================================================
   Cosmos PWA — Events Page
   ============================================================ */

import { auth, db, collection, onSnapshot, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, addDoc, increment, serverTimestamp } from '../firebase-config.js';
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

  // 1. Render Your Events (both registered and hosted)
  const registered = eventsList.filter(e => registrationsMap.get(e.id) === true || (currentUserId && e.createdBy === currentUserId));
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

async function showEventDetailsModal(outlet, event, currentUserId) {
  let modal = document.getElementById('event-details-modal');
  if (!modal) {
    modal = document.createElement('div');
    modal.className = 'modal-overlay hidden';
    modal.id = 'event-details-modal';
    modal.style.zIndex = '99999';
    document.body.appendChild(modal);
  }

  const spotsLeft = event.maxParticipants - event.participantCount;
  const isJoined = registrationsMap.get(event.id) || false;
  const isHost = currentUserId && event.createdBy === currentUserId;

  const defaultName = window.cosmosApp?.userProfile?.name || auth.currentUser?.displayName || '';
  const defaultEmail = auth.currentUser?.email || window.cosmosApp?.userProfile?.email || '';

  modal.innerHTML = `
    <div class="modal-card" style="max-width:480px; position:relative; overflow:hidden;">
      <button class="modal-close" id="btn-close-details-modal" aria-label="Close modal" style="position:absolute; right:12px; top:12px; border:none; background:rgba(0,0,0,0.6); border-radius:50%; width:32px; height:32px; display:flex; align-items:center; justify-content:center; color:white; cursor:pointer; z-index:10; transition:all 0.2s;">✕</button>
      
      <div class="event-card-cover" style="height: 160px; margin: -1.5rem -1.5rem 1.25rem -1.5rem; overflow: hidden; ${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? `background: ${getGradientCss(event.coverUrl)};` : ''}">
        ${(event.coverUrl || 'gradient:cosmos-glow').startsWith('gradient:') ? '<div style="width:100%;height:100%;display:flex;align-items:center;justify-content:center;font-size:3rem;">📅</div>' : `<img src="${event.coverUrl}" style="width: 100%; height: 100%; object-fit: cover;" alt="${event.title}" />`}
      </div>
      
      <div class="event-modal-content" style="max-height: calc(85vh - 180px); overflow-y: auto; padding-right: 4px;">
        <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:0.5rem;">
          <div class="event-host-badge" data-creator-id="${event.createdBy}">
            <div class="host-avatar-placeholder"><div class="host-avatar-initial">${event.title.charAt(0)}</div></div>
            <span class="host-name">Cosmos Member</span>
          </div>
          ${isHost ? '<span class="event-status-pill hosting">👑 Hosted by You</span>' : (isJoined ? '<span class="event-status-pill joined">✓ Participating</span>' : '')}
        </div>

        <h3 style="font-family:var(--font-display); font-size:1.35rem; font-weight:800; color:white; margin:0.25rem 0 0 0;">${event.title}</h3>
        
        <div style="display:flex; flex-direction:column; gap:0.4rem; margin-top:0.25rem;">
          <div class="luma-card-meta">
            <svg class="luma-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            <span style="color:var(--text-primary); font-weight:600;">${event.date}</span>
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
            <span>${spotsLeft > 0 ? `${spotsLeft} spots remaining` : 'Sold out'} (${event.participantCount} registered)</span>
          </div>
        </div>
        
        <div style="background:rgba(255,255,255,0.02); border:1px solid rgba(255,255,255,0.05); border-radius:12px; padding:0.85rem; margin-top:0.25rem;">
          <h5 style="margin:0 0 0.4rem 0; font-size:0.8rem; text-transform:uppercase; letter-spacing:0.5px; color:var(--text-muted);">About Event</h5>
          <p style="font-size:0.88rem; color:var(--text-secondary); line-height:1.6; margin:0; white-space:pre-wrap;">${event.description || 'No description provided.'}</p>
        </div>

        ${isHost ? `
          <!-- HOST VIEW: PARTICIPANTS ROSTER & ACTIONS -->
          <div class="event-host-admin-panel" id="host-registrants-panel">
            <div class="event-host-admin-header">
              <span class="event-host-admin-title">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                Registered Participants
              </span>
              <span class="event-registrant-badge" id="host-registrant-count">Loading...</span>
            </div>
            <div class="event-registrants-list" id="host-registrants-list">
              <div style="text-align:center; padding:1rem; color:var(--text-muted); font-size:0.82rem;">
                <div class="loading-spinner" style="width:20px;height:20px;margin:0 auto 0.5rem auto;display:block;"></div>
                Loading participant details...
              </div>
            </div>
            
            <div class="event-host-actions">
              <button class="btn-event-action btn-event-edit" id="btn-edit-event" data-event-id="${event.id}">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                Edit Event
              </button>
              <button class="btn-event-action btn-event-delete" id="btn-delete-event" data-event-id="${event.id}">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>
                Delete Event
              </button>
            </div>
          </div>
        ` : (isJoined ? `
          <!-- ALREADY REGISTERED VIEW -->
          <div style="background:rgba(16,185,129,0.08); border:1px solid rgba(16,185,129,0.25); border-radius:14px; padding:1rem; text-align:center; margin-top:0.5rem;">
            <div style="font-size:1.5rem; margin-bottom:0.25rem;">🎉</div>
            <h4 style="margin:0; font-size:0.95rem; color:#34d399; font-weight:700;">You're on the Guest List</h4>
            <p style="margin:0.25rem 0 0 0; font-size:0.8rem; color:var(--text-secondary);">We'll notify you when the session begins.</p>
          </div>
        ` : (spotsLeft <= 0 ? `
          <!-- FULL VIEW -->
          <div style="background:rgba(239,68,68,0.08); border:1px solid rgba(239,68,68,0.25); border-radius:14px; padding:1rem; text-align:center; margin-top:0.5rem;">
            <h4 style="margin:0; font-size:0.95rem; color:var(--red); font-weight:700;">Event is Full</h4>
            <p style="margin:0.25rem 0 0 0; font-size:0.8rem; color:var(--text-secondary);">All available participant slots have been claimed.</p>
          </div>
        ` : `
          <!-- PARTICIPATION FORM -->
          <form class="event-participate-form" id="event-participate-form">
            <h4 class="event-participate-title">${event.isPaid ? 'Book Ticket (Razorpay Checkout)' : 'Participate in this Event'}</h4>
            <p class="event-participate-subtitle">${event.isPaid ? 'Secure instant digital pass via centralized Razorpay gateway.' : 'Confirm your name and email to join the attendee list.'}</p>
            
            ${event.isPaid ? `
              <div style="background:rgba(108,99,255,0.08); border:1px solid rgba(108,99,255,0.25); border-radius:12px; padding:0.75rem 0.9rem; margin-bottom:0.75rem; display:flex; justify-content:space-between; align-items:center;">
                <div>
                  <div style="font-size:0.72rem; color:var(--text-muted); text-transform:uppercase; font-weight:700; letter-spacing:0.5px;">Ticket Price</div>
                  <div style="font-size:1.15rem; font-weight:800; color:var(--accent-primary);">${event.price || '₹' + (event.priceAmount || 0)}</div>
                </div>
                <div style="font-size:0.75rem; color:#34d399; font-weight:600; background:rgba(16,185,129,0.1); padding:4px 8px; border-radius:6px;">
                  🔒 Secured by Razorpay
                </div>
              </div>
            ` : ''}

            <div class="form-group" style="margin-bottom:0.4rem;">
              <label class="form-label" for="participate-name" style="font-size:0.78rem;">Full Name *</label>
              <input class="form-input" type="text" id="participate-name" value="${defaultName}" required placeholder="Your full name" style="padding:0.65rem 0.85rem; font-size:0.88rem;" />
            </div>

            <div class="form-group" style="margin-bottom:0.75rem;">
              <label class="form-label" for="participate-email" style="font-size:0.78rem;">Email Address *</label>
              <input class="form-input" type="email" id="participate-email" value="${defaultEmail}" required placeholder="name@example.com" style="padding:0.65rem 0.85rem; font-size:0.88rem;" />
            </div>

            <button type="submit" class="btn btn-primary" id="btn-submit-participate" style="width:100%; py:12px; font-weight:700; font-size:0.92rem;">
              ${event.isPaid ? `Pay ${event.price || '₹' + (event.priceAmount || 0)} & Book Ticket 💳` : 'Confirm Participation 🚀'}
            </button>
          </form>
        `))}
      </div>
    </div>
  `;

  resolveHostProfiles(modal);

  // Open modal with smooth transition
  modal.classList.remove('hidden');
  modal.offsetHeight; // force reflow for CSS transition
  modal.classList.add('active');
  document.body.style.overflow = 'hidden';

  const closeModal = () => {
    modal.classList.remove('active');
    setTimeout(() => {
      if (!modal.classList.contains('active')) {
        modal.classList.add('hidden');
      }
    }, 300);
    document.body.style.overflow = '';
  };

  const btnClose = modal.querySelector('#btn-close-details-modal');
  if (btnClose) btnClose.onclick = closeModal;

  modal.onclick = (e) => {
    if (e.target === modal) closeModal();
  };

  // If host is viewing, load real-time participants and wire edit/delete actions
  if (isHost) {
    const btnEditEvent = modal.querySelector('#btn-edit-event');
    const btnDeleteEvent = modal.querySelector('#btn-delete-event');

    if (btnEditEvent) {
      btnEditEvent.addEventListener('click', () => {
        closeModal();
        if (window.cosmosOpenEditEvent) {
          window.cosmosOpenEditEvent(event);
        } else {
          window.location.hash = '#/organize';
        }
      });
    }

    if (btnDeleteEvent) {
      btnDeleteEvent.addEventListener('click', () => {
        showDeleteEventConfirmationInEvents(event, closeModal);
      });
    }

    try {
      const regSnap = await getDocs(collection(db, 'events', event.id, 'registrants'));
      const countEl = modal.querySelector('#host-registrant-count');
      const listEl = modal.querySelector('#host-registrants-list');

      if (countEl && listEl) {
        const registrants = [];
        regSnap.forEach(d => {
          const data = d.data() || {};
          registrants.push({
            id: d.id,
            name: data.name || 'Anonymous Member',
            email: data.email || 'No email provided',
            registeredAt: data.registeredAt?.toDate ? data.registeredAt.toDate() : null
          });
        });

        countEl.textContent = `${registrants.length} ${registrants.length === 1 ? 'Person' : 'People'}`;

        if (registrants.length === 0) {
          listEl.innerHTML = `
            <div style="text-align:center; padding:1.25rem; color:var(--text-secondary); font-size:0.85rem;">
              <div style="font-size:1.75rem; margin-bottom:0.25rem;">📭</div>
              <strong>No participants yet</strong>
              <div style="font-size:0.78rem; color:var(--text-muted); margin-top:0.2rem;">Share your event to get attendees registered!</div>
            </div>
          `;
        } else {
          listEl.innerHTML = registrants.map(r => {
            const initial = (r.name || 'M').charAt(0).toUpperCase();
            const dateStr = r.registeredAt ? r.registeredAt.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : 'Recently';
            return `
              <div class="event-registrant-item">
                <div class="event-registrant-avatar">${initial}</div>
                <div class="event-registrant-info">
                  <div class="event-registrant-name">${r.name}</div>
                  <div class="event-registrant-email">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
                    <a href="mailto:${r.email}" style="color:var(--text-secondary); text-decoration:none;" onclick="event.stopPropagation();">${r.email}</a>
                  </div>
                </div>
                <div style="font-size:0.72rem; color:var(--text-muted); text-align:right;">${dateStr}</div>
              </div>
            `;
          }).join('');
        }
      }
    } catch (err) {
      console.error('[Cosmos Events] Error fetching registrants for host:', err);
    }
  }

  // Handle Participation Form Submission
  const participateForm = modal.querySelector('#event-participate-form');
  if (participateForm) {
    participateForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const nameInput = modal.querySelector('#participate-name');
      const emailInput = modal.querySelector('#participate-email');
      const submitBtn = modal.querySelector('#btn-submit-participate');

      const name = nameInput.value.trim();
      const email = emailInput.value.trim();

      if (!name) {
        showToast('Please enter your full name.', 'error');
        nameInput.focus();
        return;
      }
      if (!email || !email.includes('@')) {
        showToast('Please enter a valid email address.', 'error');
        emailInput.focus();
        return;
      }

      // Paid Event Razorpay Checkout
      if (event.isPaid) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Initializing Secure Checkout...';

        try {
          await loadRazorpaySdk();

          const orderRes = await fetch('https://us-central1-cosmos-app-42ed2.cloudfunctions.net/createEventTicketOrder', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              eventId: event.id,
              uid: currentUserId,
              userName: name,
              userEmail: email
            })
          });

          const orderData = await orderRes.json();
          if (!orderData.success) {
            throw new Error(orderData.error || 'Failed to initialize ticket order');
          }

          submitBtn.textContent = 'Awaiting Razorpay Payment...';

          const rzp = new window.Razorpay({
            key: orderData.keyId,
            amount: orderData.amountInPaise,
            currency: orderData.currency || 'INR',
            name: 'COSMOS Events',
            description: `Pass: ${event.title}`,
            order_id: orderData.orderId,
            prefill: {
              name: name,
              email: email
            },
            theme: {
              color: '#6C63FF'
            },
            handler: async function (response) {
              submitBtn.textContent = 'Verifying Ticket Payment...';
              try {
                const verifyRes = await fetch('https://us-central1-cosmos-app-42ed2.cloudfunctions.net/verifyEventTicketPayment', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({
                    eventId: event.id,
                    uid: currentUserId,
                    orderId: response.razorpay_order_id,
                    paymentId: response.razorpay_payment_id,
                    signature: response.razorpay_signature,
                    userName: name,
                    userEmail: email
                  })
                });

                const verifyData = await verifyRes.json();
                if (!verifyData.success) {
                  throw new Error(verifyData.error || 'Payment verification failed');
                }

                registrationsMap.set(event.id, true);
                event.participantCount += 1;

                showToast(`🎉 Ticket Confirmed! Pass Receipt: ${verifyData.receiptId}`, 'success');
                showEventDetailsModal(outlet, event, currentUserId);
                updateEventsDisplay(outlet, currentUserId);
              } catch (vErr) {
                console.error('[Cosmos Events] Verification error:', vErr);
                showToast('Payment verification issue: ' + vErr.message, 'error');
                submitBtn.disabled = false;
                submitBtn.textContent = `Pay ${event.price || '₹' + (event.priceAmount || 0)} & Book Ticket 💳`;
              }
            },
            modal: {
              ondismiss: function () {
                submitBtn.disabled = false;
                submitBtn.textContent = `Pay ${event.price || '₹' + (event.priceAmount || 0)} & Book Ticket 💳`;
              }
            }
          });

          rzp.open();
          return;
        } catch (err) {
          console.error('[Cosmos Events] Razorpay checkout error:', err);
          showToast('Checkout error: ' + (err.message || 'Unknown error'), 'error');
          submitBtn.disabled = false;
          submitBtn.textContent = `Pay ${event.price || '₹' + (event.priceAmount || 0)} & Book Ticket 💳`;
          return;
        }
      }

      // Free Event Direct Registration
      submitBtn.disabled = true;
      submitBtn.textContent = 'Registering...';

      try {
        await setDoc(doc(db, 'events', event.id, 'registrants', currentUserId), {
          userId: currentUserId,
          name: name,
          email: email,
          registeredAt: serverTimestamp()
        });

        await updateDoc(doc(db, 'events', event.id), {
          participantCount: increment(1)
        });

        await addDoc(collection(db, 'notifications'), {
          userId: currentUserId,
          type: 'EVENT_REMINDER',
          title: `Registered for ${event.title}`,
          body: "You're all set! We'll remind you when the session starts.",
          timestamp: serverTimestamp(),
          isRead: false,
          actionId: event.id
        });

        if (event.createdBy && event.createdBy !== currentUserId) {
          await addDoc(collection(db, 'notifications'), {
            userId: event.createdBy,
            type: 'EVENT_REGISTRATION',
            title: `New Participant: ${name}`,
            body: `${name} (${email}) registered for ${event.title}.`,
            timestamp: serverTimestamp(),
            isRead: false,
            actionId: event.id
          });
        }

        registrationsMap.set(event.id, true);
        event.participantCount += 1;

        showToast(`🎉 You're participating in ${event.title}!`, 'success');
        
        showEventDetailsModal(outlet, event, currentUserId);
        updateEventsDisplay(outlet, currentUserId);
      } catch (err) {
        console.error('[Cosmos Events] Registration error:', err);
        showToast('Failed to register: ' + (err.message || 'Unknown error'), 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Confirm Participation 🚀';
      }
    });
  }
}

function loadRazorpaySdk() {
  return new Promise((resolve, reject) => {
    if (window.Razorpay) {
      resolve(window.Razorpay);
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.async = true;
    script.onload = () => resolve(window.Razorpay);
    script.onerror = () => reject(new Error('Failed to load Razorpay SDK'));
    document.head.appendChild(script);
  });
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

function showDeleteEventConfirmationInEvents(event, closeDetailsModal) {
  let confirmModal = document.getElementById('delete-event-confirm-modal');
  if (!confirmModal) {
    confirmModal = document.createElement('div');
    confirmModal.className = 'modal-overlay hidden';
    confirmModal.id = 'delete-event-confirm-modal';
    confirmModal.style.zIndex = '100001';
    document.body.appendChild(confirmModal);
  }

  confirmModal.innerHTML = `
    <div class="modal-card event-confirm-dialog anim-scale-in">
      <div class="event-confirm-icon">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>
      </div>
      <h3 class="event-confirm-title">Delete Event?</h3>
      <p class="event-confirm-desc">
        Are you sure you want to delete <strong style="color:var(--text-primary);">"${event.title}"</strong>? This will permanently remove the event and all participant registrations.
      </p>
      <div class="event-confirm-actions">
        <button class="btn btn-secondary" id="btn-cancel-delete-event">Cancel</button>
        <button class="btn btn-danger" id="btn-confirm-delete-event" style="background:var(--gradient-danger);color:#fff;border:none;">Delete Event</button>
      </div>
    </div>
  `;

  confirmModal.classList.remove('hidden');
  confirmModal.offsetHeight;
  confirmModal.classList.add('active');

  const closeConfirmModal = () => {
    confirmModal.classList.remove('active');
    setTimeout(() => {
      if (!confirmModal.classList.contains('active')) {
        confirmModal.classList.add('hidden');
      }
    }, 300);
  };

  const btnCancel = confirmModal.querySelector('#btn-cancel-delete-event');
  const btnConfirm = confirmModal.querySelector('#btn-confirm-delete-event');

  if (btnCancel) btnCancel.onclick = closeConfirmModal;

  confirmModal.onclick = (e) => {
    if (e.target === confirmModal) closeConfirmModal();
  };

  if (btnConfirm) {
    btnConfirm.onclick = async () => {
      btnConfirm.disabled = true;
      btnConfirm.textContent = 'Deleting...';
      if (btnCancel) btnCancel.disabled = true;

      try {
        await deleteDoc(doc(db, 'events', event.id));
        showToast(`Event "${event.title}" deleted 🗑️`, 'info');
        closeConfirmModal();
        if (typeof closeDetailsModal === 'function') {
          closeDetailsModal();
        }
      } catch (err) {
        console.error('[Cosmos Events] Delete event error:', err);
        showToast('Failed to delete event: ' + err.message, 'error');
        btnConfirm.disabled = false;
        btnConfirm.textContent = 'Delete Event';
        if (btnCancel) btnCancel.disabled = false;
      }
    };
  }
}
