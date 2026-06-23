/* ============================================================
   Cosmos PWA — Events Page
   ============================================================ */

import { auth, db, collection, query, onSnapshot, doc, getDoc, setDoc, updateDoc, addDoc, increment, serverTimestamp } from '../firebase-config.js';
import { showToast } from '../app.js';

const tabs = ['All Events', 'Speed Networking', 'Curated Meetup', 'Invite Only', 'Industry Round'];
let activeTab = 'All Events';
let unsubEvents = null;
const registrationsMap = new Map();

export async function renderEvents(outlet) {
  const user = auth.currentUser;
  if (!user) return;

  if (unsubEvents) {
    unsubEvents();
    unsubEvents = null;
  }

  outlet.innerHTML = `
    <div class="events-page page">
      <div class="page-header">
        <h1 class="page-title">Events</h1>
        <p class="page-subtitle">Structured networking, real connections</p>
      </div>
      <div class="events-tabs" id="events-tabs">
        ${tabs.map(t => `
          <button class="event-tab ${t === activeTab ? 'active' : ''}" data-tab="${t}">${t}</button>
        `).join('')}
      </div>
      <div class="events-grid stagger" id="events-grid">
        <div class="loading-spinner" style="margin:2rem auto; display:block; grid-column: 1/-1;"></div>
      </div>
    </div>
  `;

  // Tab clicks
  outlet.querySelectorAll('.event-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      activeTab = tab.dataset.tab;
      outlet.querySelectorAll('.event-tab').forEach(t => t.classList.toggle('active', t === tab));
      // Re-trigger render
      triggerEventsFetch(outlet, user.uid);
    });
  });

  triggerEventsFetch(outlet, user.uid);
}

function triggerEventsFetch(outlet, currentUserId) {
  if (unsubEvents) {
    unsubEvents();
  }

  unsubEvents = onSnapshot(collection(db, 'events'), async (snapshot) => {
    const list = [];
    const checkRegPromises = [];

    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      const eventId = docSnap.id;

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

      list.push({
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

    const getFilteredEvents = () => {
      if (activeTab === 'All Events') return list;
      // Map standard event types to clean matching
      const tabMap = {
        'Speed Networking': 'SPEED_NETWORKING',
        'Curated Meetup': 'CURATED_MEETUP',
        'Invite Only': 'INVITE_ONLY',
        'Industry Round': 'INDUSTRY_ROUND'
      };
      const typeKey = tabMap[activeTab] || activeTab;
      return list.filter(e => e.type === typeKey);
    };

    const grid = outlet.querySelector('#events-grid');
    if (grid) {
      const filtered = getFilteredEvents();
      grid.innerHTML = renderEventCards(filtered);
      attachEventCardListeners(outlet, filtered, currentUserId);
    }
  }, (error) => {
    console.error('[Cosmos Events] Error listening to events:', error);
    const grid = outlet.querySelector('#events-grid');
    if (grid) {
      grid.innerHTML = `<div style="grid-column:1/-1;text-align:center;color:var(--red);padding:2rem;">Failed to load events: ${error.message}</div>`;
    }
  });
}

function renderEventCards(events) {
  if (!events.length) {
    return `
      <div class="empty-state" style="grid-column:1/-1;">
        <div class="empty-state-icon">📅</div>
        <h3 class="empty-state-title">No Events Found</h3>
        <p class="empty-state-desc">No upcoming events in this category. Check back soon!</p>
      </div>
    `;
  }

  const iconsMap = {
    SPEED_NETWORKING: '🔥',
    CURATED_MEETUP: '🤖',
    INVITE_ONLY: '💰',
    INDUSTRY_ROUND: '🎯',
    OPEN_NETWORKING: '🤝'
  };

  const typesReadable = {
    SPEED_NETWORKING: 'Speed Networking',
    CURATED_MEETUP: 'Curated Meetup',
    INVITE_ONLY: 'Invite Only',
    INDUSTRY_ROUND: 'Industry Round',
    OPEN_NETWORKING: 'Open Networking'
  };

  return events.map(event => {
    const spotsLeft = event.maxParticipants - event.participantCount;
    const urgency = spotsLeft <= 10;
    const icon = iconsMap[event.type] || '📅';
    const typeText = typesReadable[event.type] || event.type;
    const isJoined = registrationsMap.get(event.id) || false;

    return `
      <div class="event-card anim-fade-up" data-id="${event.id}">
        <div class="event-card-header">
          <div>
            <div class="event-card-title">${icon} ${event.title}</div>
            <span class="badge badge-purple" style="margin-top:0.25rem;">${typeText}</span>
          </div>
        </div>
        <div class="event-card-meta">
          <span class="event-card-meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            ${event.date}
          </span>
          <span class="event-card-meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            ${event.time}
          </span>
          <span class="event-card-meta-item" ${urgency && spotsLeft > 0 ? 'style="color:var(--amber);"' : ''}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
            ${spotsLeft > 0 ? `${spotsLeft} spots left` : 'Sold out'}
          </span>
        </div>
        <p style="font-size:0.85rem;color:var(--text-secondary);margin-bottom:0.75rem;line-height:1.6;">${event.description}</p>
        <div class="event-card-tags" style="margin-bottom:0.75rem;">
          ${event.tags.map(t => `<span class="tag">${t}</span>`).join('')}
        </div>
        <div class="event-card-footer">
          <div class="event-card-attendees">
            <span class="event-card-spots">${event.participantCount} going</span>
          </div>
          <button class="btn ${isJoined ? 'btn-success' : 'btn-primary'} btn-sm event-join-btn" data-event-id="${event.id}" ${isJoined || spotsLeft <= 0 ? 'disabled' : ''}>
            ${isJoined ? 'Joined! ✓' : (spotsLeft <= 0 ? 'Full' : 'Join Event')}
          </button>
        </div>
      </div>
    `;
  }).join('');
}

function attachEventCardListeners(outlet, events, currentUserId) {
  outlet.querySelectorAll('.event-join-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const eventId = btn.dataset.eventId;
      const event = events.find(ev => ev.id === eventId);
      if (!event) return;

      btn.disabled = true;
      btn.textContent = 'Joining...';

      try {
        const eventRef = doc(db, 'events', eventId);
        const registrantRef = doc(db, 'events', eventId, 'registrants', currentUserId);

        // Save registration and increment attendee counter
        await setDoc(registrantRef, { registeredAt: serverTimestamp() });
        await updateDoc(eventRef, { participantCount: increment(1) });

        // Add a notification for reminder
        await addDoc(collection(db, 'notifications'), {
          userId: currentUserId,
          type: 'EVENT_REMINDER',
          title: `Registered for ${event.title}`,
          body: `You're all set! We'll remind you when the event starts.`,
          timestamp: serverTimestamp(),
          isRead: false,
          actionId: eventId
        });

        registrationsMap.set(eventId, true);
        btn.textContent = 'Joined! ✓';
        btn.classList.remove('btn-primary');
        btn.classList.add('btn-success');
        showToast(`You've joined "${event.title}"! 🎉`, 'success');
      } catch (err) {
        console.error('[Cosmos Events] Join failed:', err);
        showToast('Failed to join event', 'error');
        btn.disabled = false;
        btn.textContent = 'Join Event';
      }
    });
  });
}

