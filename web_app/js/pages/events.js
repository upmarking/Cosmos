/* ============================================================
   Cosmos PWA — Events Page
   ============================================================ */

import { db, collection, getDocs, query, orderBy, limit } from '../firebase-config.js';
import { showToast } from '../app.js';

const demoEvents = [
  { id: '1', title: 'Founders Speed Network', type: 'Speed Networking', icon: '🔥', date: 'Jun 24, 2026', time: '6:00 PM IST', spots: 18, totalSpots: 30, tags: ['Founders', 'B2B', 'AI'], attendees: ['SK', 'MR', 'AP'], description: 'Connect with 6 founders in structured 15-minute rounds. AI summaries generated after each meeting.' },
  { id: '2', title: 'AI Builders Meetup', type: 'Curated Meetup', icon: '🤖', date: 'Jun 26, 2026', time: '7:30 PM IST', spots: 24, totalSpots: 40, tags: ['AI', 'ML', 'Engineering'], attendees: ['JO', 'EV', 'KT', 'SC'], description: 'Deep dive into the latest in AI infrastructure with fellow builders and engineers.' },
  { id: '3', title: 'Investor-Founder Match', type: 'Invite Only', icon: '💰', date: 'Jun 28, 2026', time: '5:00 PM IST', spots: 8, totalSpots: 20, tags: ['Fundraising', 'Pre-Seed', 'Seed'], attendees: ['MR', 'AP'], description: 'Exclusive curated matching between active investors and fundable founders. Limited spots.' },
  { id: '4', title: 'Product Leaders Circle', type: 'Industry Round', icon: '🎯', date: 'Jul 1, 2026', time: '6:30 PM IST', spots: 32, totalSpots: 50, tags: ['Product', 'Design', 'Growth'], attendees: ['KT', 'AP', 'EV'], description: 'Monthly gathering of product leaders sharing insights, challenges, and frameworks.' },
  { id: '5', title: 'Women in Tech Network', type: 'Themed Session', icon: '💜', date: 'Jul 3, 2026', time: '7:00 PM IST', spots: 15, totalSpots: 25, tags: ['Women', 'Leadership', 'Tech'], attendees: ['SC', 'AP', 'EV'], description: 'Building connections and mentoring relationships among women leaders in technology.' },
];

const tabs = ['All Events', 'Speed Networking', 'Curated Meetup', 'Invite Only', 'Industry Round'];
let activeTab = 'All Events';

export async function renderEvents(outlet) {
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
        ${renderEventCards(getFilteredEvents())}
      </div>
    </div>
  `;

  // Tab clicks
  outlet.querySelectorAll('.event-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      activeTab = tab.dataset.tab;
      outlet.querySelectorAll('.event-tab').forEach(t => t.classList.toggle('active', t === tab));
      const grid = outlet.querySelector('#events-grid');
      grid.innerHTML = renderEventCards(getFilteredEvents());
      attachEventCardListeners(outlet);
    });
  });

  attachEventCardListeners(outlet);
}

function getFilteredEvents() {
  if (activeTab === 'All Events') return demoEvents;
  return demoEvents.filter(e => e.type === activeTab);
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

  return events.map(event => {
    const spotsLeft = event.totalSpots - event.spots;
    const urgency = spotsLeft <= 10;
    const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#db2777,#f472b6)', 'linear-gradient(135deg,#059669,#34d399)'];

    return `
      <div class="event-card anim-fade-up" data-id="${event.id}">
        <div class="event-card-header">
          <div>
            <div class="event-card-title">${event.icon} ${event.title}</div>
            <span class="badge badge-purple" style="margin-top:0.25rem;">${event.type}</span>
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
          <span class="event-card-meta-item ${urgency ? 'style="color:var(--amber);"' : ''}">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
            ${spotsLeft} spots left
          </span>
        </div>
        <p style="font-size:0.85rem;color:var(--text-secondary);margin-bottom:0.75rem;line-height:1.6;">${event.description}</p>
        <div class="event-card-tags">
          ${event.tags.map(t => `<span class="tag">${t}</span>`).join('')}
        </div>
        <div class="event-card-footer">
          <div class="event-card-attendees">
            ${event.attendees.map((a, i) => `<div class="avatar" style="background:${avatarColors[i % avatarColors.length]};width:28px;height:28px;font-size:0.6rem;margin-left:${i > 0 ? '-8px' : '0'};border:2px solid var(--bg-primary);">${a}</div>`).join('')}
            <span class="event-card-spots">${event.spots} going</span>
          </div>
          <button class="btn btn-primary btn-sm event-join-btn" data-event-id="${event.id}">Join Event</button>
        </div>
      </div>
    `;
  }).join('');
}

function attachEventCardListeners(outlet) {
  outlet.querySelectorAll('.event-join-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const eventId = btn.dataset.eventId;
      const event = demoEvents.find(ev => ev.id === eventId);
      if (event) {
        btn.textContent = 'Joined! ✓';
        btn.disabled = true;
        btn.classList.remove('btn-primary');
        btn.classList.add('btn-success');
        showToast(`You've joined "${event.title}"! 🎉`, 'success');
      }
    });
  });
}
