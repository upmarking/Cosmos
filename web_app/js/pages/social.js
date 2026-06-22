/* ============================================================
   Cosmos PWA — Social Feed Page
   ============================================================ */

import { showToast } from '../app.js';

const demoPosts = [
  {
    id: '1', author: 'Sarah Chen', initials: 'SC', role: 'Founder @ BuildSpark AI',
    content: '🚀 Just closed our first enterprise pilot! 3 months of building, 47 user interviews, and countless pivots later — we have paying customers.\n\nBiggest lesson: Stop building features. Start solving pain points.\n\nGrateful for this Cosmos community for the introductions that made it possible.',
    time: '2h ago', likes: 42, comments: 8, liked: false
  },
  {
    id: '2', author: 'Marcus Rivera', initials: 'MR', role: 'Angel Investor @ Horizon Capital',
    content: '📊 What I look for in pre-seed founders:\n\n1. Obsession with the problem (not the solution)\n2. Speed of learning, not speed of building\n3. Ability to tell a story that makes me lean forward\n4. A network that vouches for their character\n\nThe best founders I\'ve backed weren\'t the most technical — they were the most relentless.',
    time: '5h ago', likes: 89, comments: 23, liked: true
  },
  {
    id: '3', author: 'Elena Volkov', initials: 'EV', role: 'Startup Operator @ Sequoia Scout',
    content: '💡 Hot take: Your first 10 hires matter more than your first 10 customers.\n\nCustomers validate your product. Hires define your culture. And culture is the one thing that compounds.',
    time: '8h ago', likes: 67, comments: 15, liked: false
  },
  {
    id: '4', author: 'Kai Tanaka', initials: 'KT', role: 'Design Director @ Figma',
    content: '🎨 Redesigned our entire onboarding flow last week. Results:\n\n• Time to first value: -40%\n• Completion rate: +28%\n• Support tickets: -65%\n\nThe secret? We removed 3 steps and added 0. Sometimes the best design is what you take away.',
    time: '1d ago', likes: 134, comments: 31, liked: false
  },
  {
    id: '5', author: 'Aisha Patel', initials: 'AP', role: 'Product Lead @ Notion',
    content: '🤝 Met an incredible CTO through a Cosmos Speed Network event last week. We talked about building AI interfaces that feel human.\n\nNow we\'re co-writing an article about it. This is what intentional networking looks like — not collecting business cards, but building something together.',
    time: '1d ago', likes: 56, comments: 9, liked: false
  },
];

export async function renderSocial(outlet) {
  outlet.innerHTML = `
    <div class="social-page page">
      <div class="page-header" style="display:flex;align-items:center;justify-content:space-between;">
        <div>
          <h1 class="page-title">Feed</h1>
          <p class="page-subtitle">Insights from your network</p>
        </div>
        <button class="btn btn-primary btn-sm" id="create-post-btn">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Post
        </button>
      </div>
      <div id="social-feed" class="stagger">
        ${renderPosts(demoPosts)}
      </div>
    </div>
  `;

  // Create post
  outlet.querySelector('#create-post-btn')?.addEventListener('click', () => {
    showCreatePostModal(outlet);
  });

  attachPostListeners(outlet);
}

function renderPosts(posts) {
  const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#d97706,#fbbf24)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#db2777,#f472b6)'];

  return posts.map((post, i) => `
    <div class="post-card anim-fade-up" data-id="${post.id}" style="animation-delay:${i * 0.06}s;">
      <div class="post-header">
        <div class="avatar" style="background:${avatarColors[i % avatarColors.length]};">${post.initials}</div>
        <div>
          <div class="post-author-name">${post.author}</div>
          <div class="post-author-role">${post.role}</div>
        </div>
        <span class="post-time">${post.time}</span>
      </div>
      <div class="post-content">${post.content.replace(/\n/g, '<br>')}</div>
      <div class="post-actions">
        <button class="post-action ${post.liked ? 'liked' : ''}" data-action="like" data-post-id="${post.id}">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="${post.liked ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
          ${post.likes}
        </button>
        <button class="post-action" data-action="comment" data-post-id="${post.id}">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          ${post.comments}
        </button>
        <button class="post-action" data-action="share" data-post-id="${post.id}">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
          Share
        </button>
      </div>
    </div>
  `).join('');
}

function attachPostListeners(outlet) {
  outlet.querySelectorAll('.post-action').forEach(btn => {
    btn.addEventListener('click', () => {
      const action = btn.dataset.action;
      const postId = btn.dataset.postId;
      const post = demoPosts.find(p => p.id === postId);

      if (action === 'like' && post) {
        post.liked = !post.liked;
        post.likes += post.liked ? 1 : -1;
        btn.classList.toggle('liked');
        const svg = btn.querySelector('svg');
        svg.setAttribute('fill', post.liked ? 'currentColor' : 'none');
        btn.innerHTML = `${svg.outerHTML} ${post.likes}`;
      } else if (action === 'comment') {
        showToast('Comments coming soon!', 'info');
      } else if (action === 'share') {
        showToast('Post link copied!', 'success');
      }
    });
  });
}

function showCreatePostModal(outlet) {
  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.innerHTML = `
    <div class="modal-content">
      <div class="modal-handle"></div>
      <h3 style="font-family:var(--font-display);font-size:1.15rem;font-weight:700;margin-bottom:1rem;">Create Post</h3>
      <div class="form-group">
        <textarea class="form-input" id="new-post-text" placeholder="Share an insight, update, or question with your network..." style="min-height:140px;"></textarea>
      </div>
      <div style="display:flex;gap:0.75rem;">
        <button class="btn btn-primary btn-full" id="submit-post">Post</button>
        <button class="btn btn-secondary" id="cancel-post">Cancel</button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  overlay.querySelector('#cancel-post')?.addEventListener('click', () => overlay.remove());
  overlay.addEventListener('click', (e) => { if (e.target === overlay) overlay.remove(); });

  overlay.querySelector('#submit-post')?.addEventListener('click', () => {
    const text = overlay.querySelector('#new-post-text').value.trim();
    if (text) {
      showToast('Post published! ✨', 'success');
      overlay.remove();
    } else {
      showToast('Please write something first', 'error');
    }
  });
}
