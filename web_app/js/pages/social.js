/* ============================================================
   Cosmos PWA — Social Feed Page
   ============================================================ */

import { auth, db, collection, query, orderBy, onSnapshot, addDoc, doc, updateDoc, arrayUnion, arrayRemove, increment, getDoc, serverTimestamp } from '../firebase-config.js';
import { showToast } from '../app.js';

let unsubSocial = null;

function formatTime(timestamp) {
  if (!timestamp) return 'Now';
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
  const diff = Date.now() - date.getTime();
  return whenDiff(diff, date);
}

function whenDiff(diff, date) {
  if (diff < 60000) return 'now';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
  return date.toLocaleDateString([], { day: 'numeric', month: 'short' });
}

export async function renderSocial(outlet) {
  const user = auth.currentUser;
  if (!user) return;

  if (unsubSocial) {
    unsubSocial();
    unsubSocial = null;
  }

  outlet.innerHTML = `
    <div class="social-page page">
      <div class="page-header" style="display:flex;align-items:center;justify-content:space-between;">
        <div>
          <h1 class="page-title">Social</h1>
          <p class="page-subtitle">Insights & updates from your network</p>
        </div>
        <button class="btn btn-primary btn-sm" id="create-post-btn">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          Post
        </button>
      </div>
      <div id="social-feed" class="stagger">
        <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
      </div>
    </div>
  `;

  const q = query(
    collection(db, 'social_posts'),
    orderBy('timestamp', 'desc')
  );

  unsubSocial = onSnapshot(q, (snapshot) => {
    const list = [];
    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      list.push({
        id: docSnap.id,
        authorId: data.authorId || '',
        authorName: data.authorName || 'Anonymous',
        authorHeadline: data.authorHeadline || 'Cosmos Member',
        authorAvatarUrl: data.authorAvatarUrl || '',
        content: data.content || '',
        timestamp: data.timestamp,
        likesCount: data.likesCount || 0,
        repliesCount: data.repliesCount || 0,
        likes: data.likes || [],
        isLinkedInConnected: data.isLinkedInConnected || false
      });
    });

    const feed = outlet.querySelector('#social-feed');
    if (feed) {
      feed.innerHTML = renderPosts(list, user.uid);
      attachPostListeners(outlet, list, user.uid);
    }
  }, (error) => {
    console.error('[Cosmos Social] Error in snapshot:', error);
    const feed = outlet.querySelector('#social-feed');
    if (feed) {
      feed.innerHTML = `
        <div class="empty-state">
          <div class="empty-state-icon">⚠️</div>
          <h3 class="empty-state-title">Error Loading Feed</h3>
          <p class="empty-state-desc">${error.message}</p>
        </div>
      `;
    }
  });

  // Create post button
  outlet.querySelector('#create-post-btn')?.addEventListener('click', () => {
    showCreatePostModal(outlet);
  });
}

function renderPosts(posts, currentUserId) {
  if (!posts.length) {
    return `
      <div class="empty-state">
        <div class="empty-state-icon">📝</div>
        <h3 class="empty-state-title">No Posts Yet</h3>
        <p class="empty-state-desc">Be the first to share an update with the Cosmos community!</p>
      </div>
    `;
  }

  const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#d97706,#fbbf24)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#db2777,#f472b6)'];

  return posts.map((post, i) => {
    const isLiked = post.likes.includes(currentUserId);
    const avatarUrl = post.authorAvatarUrl || '';
    const initials = post.authorName.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) || 'U';
    const hasPhoto = !!avatarUrl;
    const authorColorIdx = post.authorId.charCodeAt(0) || 0;
    const timeString = formatTime(post.timestamp);

    return `
      <div class="post-card anim-fade-up" data-id="${post.id}" style="animation-delay:${i * 0.06}s;">
        <div class="post-header">
          <div class="avatar" style="${hasPhoto ? '' : 'background:' + avatarColors[authorColorIdx % avatarColors.length]}">
            ${hasPhoto ? `<img src="${avatarUrl}" alt="${post.authorName}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />` : initials}
          </div>
          <div>
            <div class="post-author-name">
              ${post.authorName}
              ${post.isLinkedInConnected ? `
              <svg width="12" height="12" viewBox="0 0 24 24" fill="#0a66c2" style="margin-left:0.2rem;display:inline-block;vertical-align:middle;"><path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.779-1.75-1.75s.784-1.75 1.75-1.75 1.75.779 1.75 1.75-.784 1.75-1.75 1.75zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z"/></svg>` : ''}
            </div>
            <div class="post-author-role">${post.authorHeadline}</div>
          </div>
          <span class="post-time">${timeString}</span>
        </div>
        <div class="post-content">${post.content.replace(/\n/g, '<br>')}</div>
        <div class="post-actions">
          <button class="post-action ${isLiked ? 'liked' : ''}" data-action="like" data-post-id="${post.id}">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="${isLiked ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
            <span>${post.likesCount}</span>
          </button>
          <button class="post-action" data-action="comment" data-post-id="${post.id}">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            <span>${post.repliesCount}</span>
          </button>
          <button class="post-action" data-action="share" data-post-id="${post.id}">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
            Share
          </button>
        </div>
      </div>
    `;
  }).join('');
}

function attachPostListeners(outlet, posts, currentUserId) {
  outlet.querySelectorAll('.post-action').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const action = btn.dataset.action;
      const postId = btn.dataset.postId;
      const post = posts.find(p => p.id === postId);

      if (action === 'like' && post) {
        const isLiked = post.likes.includes(currentUserId);
        try {
          const postRef = doc(db, 'social_posts', postId);
          if (isLiked) {
            await updateDoc(postRef, {
              likes: arrayRemove(currentUserId),
              likesCount: increment(-1)
            });
          } else {
            await updateDoc(postRef, {
              likes: arrayUnion(currentUserId),
              likesCount: increment(1)
            });
          }
        } catch (err) {
          console.error('[Cosmos Social] Error liking post:', err);
          showToast('Failed to update like status', 'error');
        }
      } else if (action === 'comment' && post) {
        showCommentModal(post, currentUserId);
      } else if (action === 'share') {
        try {
          const url = `${window.location.origin}/#/social/post/${postId}`;
          await navigator.clipboard.writeText(url);
          showToast('Post link copied to clipboard!', 'success');
        } catch {
          showToast('Failed to copy post link', 'error');
        }
      }
    });
  });
}

function showCommentModal(post, currentUserId) {
  let commentUnsub = null;

  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.innerHTML = `
    <div class="modal-content" style="max-width:480px;max-height:80vh;display:flex;flex-direction:column;">
      <div class="modal-handle"></div>
      <h3 style="font-family:var(--font-display);font-size:1.15rem;font-weight:700;margin-bottom:0.75rem;">Comments</h3>
      <div id="comments-list" style="flex:1;overflow-y:auto;margin-bottom:1rem;min-height:100px;">
        <div class="loading-spinner" style="margin:1rem auto;display:block;"></div>
      </div>
      <div style="display:flex;gap:0.5rem;align-items:flex-end;">
        <textarea class="form-input" id="comment-text" placeholder="Write a comment..." style="min-height:48px;max-height:120px;resize:none;flex:1;"></textarea>
        <button class="btn btn-primary btn-sm" id="submit-comment" style="height:48px;padding:0 1rem;">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
        </button>
      </div>
    </div>
  `;

  document.body.appendChild(overlay);

  const closeModal = () => {
    if (commentUnsub) commentUnsub();
    overlay.remove();
  };

  overlay.addEventListener('click', (e) => { if (e.target === overlay) closeModal(); });

  // Real-time comments listener
  const commentsQuery = query(
    collection(db, 'social_posts', post.id, 'comments'),
    orderBy('timestamp', 'asc')
  );

  const commentsList = overlay.querySelector('#comments-list');

  commentUnsub = onSnapshot(commentsQuery, (snapshot) => {
    const comments = [];
    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      comments.push({
        id: docSnap.id,
        authorName: data.authorName || 'Anonymous',
        authorAvatarUrl: data.authorAvatarUrl || '',
        content: data.content || '',
        timestamp: data.timestamp
      });
    });

    if (comments.length === 0) {
      commentsList.innerHTML = `
        <div style="text-align:center;color:var(--text-muted);padding:2rem 0;font-size:0.88rem;">
          No comments yet. Be the first to reply!
        </div>
      `;
    } else {
      const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#d97706,#fbbf24)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#db2777,#f472b6)'];

      commentsList.innerHTML = comments.map((c, i) => {
        const initials = c.authorName.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) || 'U';
        const hasPhoto = !!c.authorAvatarUrl;
        const colorIdx = c.authorName.charCodeAt(0) || 0;
        const time = formatTime(c.timestamp);

        return `
          <div class="comment-item anim-fade-up" style="display:flex;gap:0.75rem;padding:0.625rem 0;border-bottom:1px solid var(--border);animation-delay:${i * 0.03}s;">
            <div class="avatar avatar-sm" style="${hasPhoto ? '' : 'background:' + avatarColors[colorIdx % avatarColors.length]}">
              ${hasPhoto ? `<img src="${c.authorAvatarUrl}" alt="${c.authorName}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />` : initials}
            </div>
            <div style="flex:1;min-width:0;">
              <div style="display:flex;align-items:center;gap:0.5rem;">
                <span style="font-weight:600;font-size:0.85rem;">${c.authorName}</span>
                <span style="font-size:0.72rem;color:var(--text-muted);">${time}</span>
              </div>
              <div style="font-size:0.85rem;color:var(--text-secondary);margin-top:0.15rem;line-height:1.5;">${c.content.replace(/\n/g, '<br>')}</div>
            </div>
          </div>
        `;
      }).join('');
      commentsList.scrollTop = commentsList.scrollHeight;
    }
  }, (error) => {
    console.error('[Cosmos Social] Comments error:', error);
    commentsList.innerHTML = `<div style="text-align:center;color:var(--red);padding:1rem;">Failed to load comments</div>`;
  });

  // Submit comment
  overlay.querySelector('#submit-comment')?.addEventListener('click', async () => {
    const text = overlay.querySelector('#comment-text').value.trim();
    if (!text) return;

    const user = auth.currentUser;
    if (!user) return;

    overlay.querySelector('#comment-text').value = '';

    try {
      const userSnap = await getDoc(doc(db, 'users', user.uid));
      const profile = userSnap.exists() ? userSnap.data() : {};

      await addDoc(collection(db, 'social_posts', post.id, 'comments'), {
        authorId: user.uid,
        authorName: profile.name || user.displayName || 'Builder',
        authorAvatarUrl: profile.avatarUrl || user.photoURL || '',
        content: text,
        timestamp: serverTimestamp()
      });

      // Increment reply count on the post
      await updateDoc(doc(db, 'social_posts', post.id), {
        repliesCount: increment(1)
      });
    } catch (err) {
      console.error('[Cosmos Social] Error posting comment:', err);
      showToast('Failed to post comment', 'error');
    }
  });

  // Enter to submit (Shift+Enter for newline)
  overlay.querySelector('#comment-text')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      overlay.querySelector('#submit-comment')?.click();
    }
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

  overlay.querySelector('#submit-post')?.addEventListener('click', async () => {
    const text = overlay.querySelector('#new-post-text').value.trim();
    if (!text) {
      showToast('Please write something first', 'error');
      return;
    }

    const user = auth.currentUser;
    if (!user) return;

    try {
      const snap = await getDoc(doc(db, 'users', user.uid));
      const profile = snap.exists() ? snap.data() : {};

      await addDoc(collection(db, 'social_posts'), {
        authorId: user.uid,
        authorName: profile.name || user.displayName || user.email?.split('@')[0] || 'Builder',
        authorHeadline: profile.headline || profile.role || 'Cosmos Member',
        authorAvatarUrl: profile.avatarUrl || user.photoURL || '',
        content: text,
        timestamp: serverTimestamp(),
        likesCount: 0,
        repliesCount: 0,
        likes: [],
        isLinkedInConnected: profile.isLinkedInConnected || false
      });

      showToast('Post published! ✨', 'success');
      overlay.remove();
    } catch (e) {
      console.error('[Cosmos Social] Error creating post:', e);
      showToast('Failed to publish post', 'error');
    }
  });
}

