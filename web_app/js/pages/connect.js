/* ============================================================
   Cosmos PWA — Connect Page (Swipe Discovery)
   ============================================================ */

import { db, collection, getDocs, query, where, limit, doc, getDoc, addDoc, serverTimestamp } from '../firebase-config.js';
import { showToast } from '../app.js';

// Demo profiles for when Firestore data isn't available
const demoProfiles = [
  { id: '1', displayName: 'Sarah Chen', headline: 'Founder & CEO', company: 'BuildSpark AI', tags: ['AI', 'SaaS', 'B2B'], bio: 'Building the future of AI-powered sales tools. Looking for a technical co-founder and early stage investors who understand the enterprise market.', matchScore: 96, mutualConnections: 3, linkedIn: true, initials: 'SC' },
  { id: '2', displayName: 'Marcus Rivera', headline: 'Angel Investor', company: 'Horizon Capital', tags: ['Fintech', 'Web3', 'Seed Stage'], bio: 'Investing in founders who are obsessed with solving real problems. 12 years in fintech, 40+ portfolio companies.', matchScore: 91, mutualConnections: 5, linkedIn: true, initials: 'MR' },
  { id: '3', displayName: 'Aisha Patel', headline: 'Product Lead', company: 'Notion', tags: ['Product', 'Design', 'Growth'], bio: 'Former PM at Google. Now leading product at Notion. Passionate about building tools that help people think better.', matchScore: 88, mutualConnections: 2, linkedIn: true, initials: 'AP' },
  { id: '4', displayName: 'James Okafor', headline: 'CTO & Co-founder', company: 'DataVerse', tags: ['Data', 'ML', 'Infrastructure'], bio: 'Building scalable data infrastructure for startups. Previously at AWS and Stripe. Open to technical advisors.', matchScore: 85, mutualConnections: 1, linkedIn: false, initials: 'JO' },
  { id: '5', displayName: 'Elena Volkov', headline: 'Startup Operator', company: 'Sequoia Scout', tags: ['Operations', 'Strategy', 'Hiring'], bio: 'Helping early-stage startups scale from 0-1. Advisor to 8 YC companies. Let's talk about what keeps you up at night.', matchScore: 93, mutualConnections: 4, linkedIn: true, initials: 'EV' },
  { id: '6', displayName: 'Kai Tanaka', headline: 'Design Director', company: 'Figma', tags: ['Design', 'Brand', 'UX'], bio: 'Leading design systems at Figma. Believe that great design is invisible. Looking to mentor early-stage founders on product design.', matchScore: 82, mutualConnections: 0, linkedIn: true, initials: 'KT' },
];

let currentIndex = 0;
let startX = 0;
let currentX = 0;
let isDragging = false;

export async function renderConnect(outlet) {
  currentIndex = 0;

  const connectionsLeft = 10 - (parseInt(localStorage.getItem('cosmos-connections-month') || '0'));

  outlet.innerHTML = `
    <div class="connect-page">
      <div class="connect-header">
        <div class="page-header" style="margin-bottom:0;">
          <h1 class="page-title">Discover</h1>
          <p class="page-subtitle">Find your next meaningful connection</p>
        </div>
        <div class="connect-counter">
          <strong>${connectionsLeft}</strong> / 10 left
        </div>
      </div>
      <div class="swipe-area" id="swipe-area">
        ${renderProfileCard(demoProfiles[0])}
      </div>
      <div class="swipe-actions" id="swipe-actions">
        <button class="swipe-btn swipe-btn-skip" id="btn-skip" aria-label="Skip">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
        <button class="swipe-btn swipe-btn-connect" id="btn-connect" aria-label="Connect">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
        </button>
        <button class="swipe-btn swipe-btn-info" id="btn-info" aria-label="More info">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
        </button>
      </div>
    </div>
  `;

  attachSwipeListeners(outlet);
}

function renderProfileCard(profile) {
  if (!profile) {
    return `
      <div class="card" style="height:100%;display:flex;align-items:center;justify-content:center;">
        <div class="empty-state">
          <div class="empty-state-icon">🌟</div>
          <h3 class="empty-state-title">You're All Caught Up!</h3>
          <p class="empty-state-desc">Check back later for new profiles that match your interests.</p>
        </div>
      </div>
    `;
  }

  const colors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#db2777,#f472b6)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#d97706,#fbbf24)'];
  const bg = colors[currentIndex % colors.length];

  return `
    <div class="profile-card" id="profile-card" data-id="${profile.id}">
      <div class="swipe-indicator swipe-indicator-right" id="indicator-right">CONNECT</div>
      <div class="swipe-indicator swipe-indicator-left" id="indicator-left">SKIP</div>
      <div class="profile-card-bg"></div>
      <div class="profile-card-avatar">
        <div class="avatar avatar-lg" style="background:${bg};width:80px;height:80px;font-size:1.5rem;">${profile.initials}</div>
      </div>
      <div class="profile-card-info">
        <div class="profile-card-name">${profile.displayName}</div>
        <div class="profile-card-role">${profile.headline} @ ${profile.company}</div>
        ${profile.linkedIn ? '<div class="profile-card-linkedin"><svg width="14" height="14" viewBox="0 0 24 24" fill="#0a66c2"><path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 0 1-2.063-2.065 2.064 2.064 0 1 1 2.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/></svg> LinkedIn Verified</div>' : ''}
        <div class="profile-card-tags">
          ${profile.tags.map((t, i) => {
            const cls = ['tag', 'tag-blue', 'tag-pink', 'tag-green', 'tag-amber'][i % 5];
            return `<span class="${cls}">${t}</span>`;
          }).join('')}
        </div>
        <div class="profile-card-bio">${profile.bio}</div>
        <div class="profile-card-match">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
          ${profile.matchScore}% Match
        </div>
        ${profile.mutualConnections > 0 ? `<div class="profile-card-mutual">👥 ${profile.mutualConnections} mutual connection${profile.mutualConnections > 1 ? 's' : ''}</div>` : ''}
      </div>
    </div>
  `;
}

function attachSwipeListeners(outlet) {
  const area = outlet.querySelector('#swipe-area');
  const skipBtn = outlet.querySelector('#btn-skip');
  const connectBtn = outlet.querySelector('#btn-connect');
  const infoBtn = outlet.querySelector('#btn-info');

  // Button actions
  skipBtn?.addEventListener('click', () => swipeOut('left', area));
  connectBtn?.addEventListener('click', () => swipeOut('right', area));
  infoBtn?.addEventListener('click', () => {
    const profile = demoProfiles[currentIndex];
    if (profile) showToast(`${profile.displayName}: ${profile.bio.slice(0, 80)}...`, 'info');
  });

  // Touch/mouse swipe
  setupDragListeners(area);
}

function setupDragListeners(area) {
  const card = area?.querySelector('#profile-card');
  if (!card) return;

  const onStart = (clientX) => {
    isDragging = true;
    startX = clientX;
    card.classList.add('swiping');
  };

  const onMove = (clientX) => {
    if (!isDragging) return;
    currentX = clientX - startX;
    const rotate = currentX * 0.08;
    card.style.transform = `translateX(${currentX}px) rotate(${rotate}deg)`;

    // Show indicators
    const rightInd = card.querySelector('#indicator-right');
    const leftInd = card.querySelector('#indicator-left');
    if (rightInd) rightInd.style.opacity = Math.min(currentX / 100, 1);
    if (leftInd) leftInd.style.opacity = Math.min(-currentX / 100, 1);
  };

  const onEnd = () => {
    if (!isDragging) return;
    isDragging = false;
    card.classList.remove('swiping');

    if (Math.abs(currentX) > 100) {
      swipeOut(currentX > 0 ? 'right' : 'left', area);
    } else {
      card.style.transform = '';
      const rightInd = card.querySelector('#indicator-right');
      const leftInd = card.querySelector('#indicator-left');
      if (rightInd) rightInd.style.opacity = 0;
      if (leftInd) leftInd.style.opacity = 0;
    }
    currentX = 0;
  };

  // Touch events
  card.addEventListener('touchstart', (e) => onStart(e.touches[0].clientX), { passive: true });
  card.addEventListener('touchmove', (e) => onMove(e.touches[0].clientX), { passive: true });
  card.addEventListener('touchend', onEnd);

  // Mouse events
  card.addEventListener('mousedown', (e) => { e.preventDefault(); onStart(e.clientX); });
  document.addEventListener('mousemove', (e) => onMove(e.clientX));
  document.addEventListener('mouseup', onEnd);
}

function swipeOut(direction, area) {
  const card = area?.querySelector('#profile-card');
  if (!card) return;

  card.classList.add(direction === 'right' ? 'swipe-right' : 'swipe-left');

  if (direction === 'right') {
    showToast(`Connected with ${demoProfiles[currentIndex]?.displayName}! 🎉`, 'success');
    const count = parseInt(localStorage.getItem('cosmos-connections-month') || '0') + 1;
    localStorage.setItem('cosmos-connections-month', count.toString());
  }

  setTimeout(() => {
    currentIndex++;
    area.innerHTML = renderProfileCard(demoProfiles[currentIndex] || null);

    // Update counter
    const counter = document.querySelector('.connect-counter');
    if (counter) {
      const left = 10 - parseInt(localStorage.getItem('cosmos-connections-month') || '0');
      counter.innerHTML = `<strong>${Math.max(0, left)}</strong> / 10 left`;
    }

    // Re-attach drag listeners for the new card
    if (demoProfiles[currentIndex]) {
      setupDragListeners(area);
    } else {
      // Hide action buttons when no more profiles
      const actions = document.querySelector('#swipe-actions');
      if (actions) actions.style.display = 'none';
    }
  }, 500);
}
