/* ============================================================
   Cosmos PWA — Communities / Orbits Page
   ============================================================ */

import { showToast } from '../app.js';

const demoOrbits = [
  { id: '1', name: 'Founders Building in AI', icon: '⚡', iconBg: 'rgba(167,139,250,0.15)', members: 234, description: 'A curated circle for founders building AI-first products. Share insights, get feedback, and find collaborators.', tags: ['AI', 'Founders', 'B2B'], posts: 48, isJoined: true },
  { id: '2', name: 'Pre-Seed Investor Network', icon: '💰', iconBg: 'rgba(251,191,36,0.15)', members: 89, description: 'Active pre-seed and seed-stage investors sharing deal flow, thesis updates, and co-investment opportunities.', tags: ['Investing', 'Pre-Seed', 'VC'], posts: 32, isJoined: true },
  { id: '3', name: 'Design-Led Startup Founders', icon: '🎨', iconBg: 'rgba(244,114,182,0.15)', members: 156, description: 'For founders who believe great products start with great design. Share your work, get critiques, and level up.', tags: ['Design', 'Product', 'UX'], posts: 67, isJoined: false },
  { id: '4', name: 'DevTools & Infrastructure', icon: '🛠️', iconBg: 'rgba(96,165,250,0.15)', members: 312, description: 'Building developer tools or infrastructure? This orbit connects the people building the stack others build on.', tags: ['DevTools', 'Infrastructure', 'Open Source'], posts: 95, isJoined: false },
  { id: '5', name: 'Climate Tech Founders', icon: '🌍', iconBg: 'rgba(52,211,153,0.15)', members: 127, description: 'Tackling climate change through technology. Connect with fellow founders, investors, and experts in the space.', tags: ['Climate', 'Sustainability', 'Impact'], posts: 41, isJoined: false },
  { id: '6', name: 'SaaS Growth Playbook', icon: '📈', iconBg: 'rgba(45,212,191,0.15)', members: 198, description: 'Growth strategies, metrics, and case studies from SaaS operators who've scaled from $0 to $10M+ ARR.', tags: ['SaaS', 'Growth', 'Revenue'], posts: 113, isJoined: true },
];

export async function renderCommunities(outlet) {
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
        ${renderOrbitCards(demoOrbits)}
      </div>
    </div>
  `;

  // Search
  const searchInput = outlet.querySelector('#orbit-search');
  searchInput?.addEventListener('input', () => {
    const q = searchInput.value.toLowerCase();
    const filtered = demoOrbits.filter(o =>
      o.name.toLowerCase().includes(q) || o.description.toLowerCase().includes(q) || o.tags.some(t => t.toLowerCase().includes(q))
    );
    outlet.querySelector('#orbits-list').innerHTML = renderOrbitCards(filtered);
    attachOrbitListeners(outlet);
  });

  attachOrbitListeners(outlet);
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

  return orbits.map((orbit, i) => `
    <div class="orbit-card anim-fade-up" data-id="${orbit.id}" style="animation-delay:${i * 0.06}s;">
      <div class="orbit-card-header">
        <div class="orbit-icon" style="background:${orbit.iconBg};">${orbit.icon}</div>
        <div>
          <div class="orbit-card-name">${orbit.name}</div>
          <div class="orbit-card-members">${orbit.members} members · ${orbit.posts} posts</div>
        </div>
      </div>
      <p class="orbit-card-desc">${orbit.description}</p>
      <div class="event-card-tags" style="margin-bottom:0.75rem;">
        ${orbit.tags.map(t => `<span class="tag">${t}</span>`).join('')}
      </div>
      <div class="orbit-card-footer">
        <div style="display:flex;gap:0.35rem;">
          ${orbit.isJoined ? '<span class="badge badge-green">✓ Member</span>' : ''}
        </div>
        <button class="btn ${orbit.isJoined ? 'btn-secondary' : 'btn-primary'} btn-sm orbit-join-btn" data-orbit-id="${orbit.id}">
          ${orbit.isJoined ? 'View Orbit' : 'Join Orbit'}
        </button>
      </div>
    </div>
  `).join('');
}

function attachOrbitListeners(outlet) {
  outlet.querySelectorAll('.orbit-join-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const orbitId = btn.dataset.orbitId;
      const orbit = demoOrbits.find(o => o.id === orbitId);
      if (orbit && !orbit.isJoined) {
        orbit.isJoined = true;
        orbit.members++;
        btn.textContent = 'View Orbit';
        btn.classList.remove('btn-primary');
        btn.classList.add('btn-secondary');
        const footer = btn.closest('.orbit-card-footer');
        const badgeArea = footer.querySelector('div');
        badgeArea.innerHTML = '<span class="badge badge-green">✓ Member</span>';
        showToast(`Joined "${orbit.name}"! 🎉`, 'success');
      } else if (orbit && orbit.isJoined) {
        showToast(`Opening "${orbit.name}" feed...`, 'info');
      }
    });
  });

  outlet.querySelectorAll('.orbit-card').forEach(card => {
    card.addEventListener('click', () => {
      const orbitId = card.dataset.id;
      const orbit = demoOrbits.find(o => o.id === orbitId);
      if (orbit) showToast(`Viewing "${orbit.name}"`, 'info');
    });
  });
}
