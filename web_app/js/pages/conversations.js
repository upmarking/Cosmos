/* ============================================================
   Cosmos PWA — Conversations Page (CRM Chat)
   ============================================================ */

import {
  auth, db, doc, getDoc, collection, query, where, orderBy, onSnapshot,
  updateDoc, addDoc, serverTimestamp, increment
} from '../firebase-config.js';
import { showToast } from '../app.js';

let unsubConnections = null;
let unsubMessages = null;
const userCache = new Map();

function formatLastMessageTime(timestamp) {
  if (!timestamp) return '';
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
  const diff = Date.now() - date.getTime();
  if (diff < 60000) return 'now';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
  if (diff < 172800000) return 'Yesterday';
  return date.toLocaleDateString([], { day: 'numeric', month: 'short' });
}

function formatMessageTime(timestamp) {
  if (!timestamp) return '';
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function getLabelColorClass(label) {
  if (!label) return '';
  const l = label.toLowerCase();
  if (l.includes('partner') || l.includes('co-founder') || l.includes('potential')) return 'badge-green';
  if (l.includes('warm') || l.includes('intro')) return 'badge-amber';
  if (l.includes('follow')) return 'badge-purple';
  if (l.includes('active') || l.includes('lead')) return 'badge-blue';
  return 'badge-purple';
}

async function getOrFetchUserProfile(uid) {
  if (!uid) return null;
  if (userCache.has(uid)) return userCache.get(uid);

  try {
    const snap = await getDoc(doc(db, 'users', uid));
    if (snap.exists()) {
      const data = snap.data();
      userCache.set(uid, data);
      return data;
    }
  } catch (e) {
    console.error("Error fetching user profile for cache:", e);
  }
  return null;
}

function cleanupListeners() {
  if (unsubConnections) {
    unsubConnections();
    unsubConnections = null;
  }
  if (unsubMessages) {
    unsubMessages();
    unsubMessages = null;
  }
}

export async function renderConversations(outlet, path) {
  const user = auth.currentUser;
  if (!user) return;

  // Check if we're viewing a specific chat
  const parts = path.split('/').filter(Boolean);
  if (parts.length > 1 && parts[1] === 'chat') {
    const activeChatId = parts[2];
    renderChatDetail(outlet, activeChatId);
    return;
  }

  cleanupListeners();

  outlet.innerHTML = `
    <div class="conversations-page page">
      <div class="page-header">
        <h1 class="page-title">Conversations</h1>
        <p class="page-subtitle">Your relationship workspace</p>
      </div>
      <div class="conversations-search">
        <div class="search-wrap">
          <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input class="search-input" type="text" id="convo-search" placeholder="Search conversations..." />
        </div>
      </div>
      <div class="conversations-list stagger" id="convo-list">
        <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
      </div>
    </div>
  `;

  const q = query(
    collection(db, 'connections'),
    where('members', 'array-contains', user.uid)
  );

  unsubConnections = onSnapshot(q, async (snapshot) => {
    const list = [];
    const otherUidsToFetch = [];

    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      const members = data.members || [];
      const otherId = members.find(id => id !== user.uid) || '';

      if (otherId && !userCache.has(otherId)) {
        otherUidsToFetch.push(otherId);
      }

      list.push({
        id: docSnap.id,
        otherId,
        lastMessage: data.lastMessage || 'Connection established! Say hello.',
        lastMessageTime: data.lastMessageTime,
        unreadCount: (data.unreadCountMap && data.unreadCountMap[user.uid]) || 0,
        labels: (data.labels && data.labels[user.uid]) || [],
        status: data.status || 'ACTIVE'
      });
    });

    if (otherUidsToFetch.length > 0) {
      await Promise.all(otherUidsToFetch.map(async (uid) => {
        try {
          const snap = await getDoc(doc(db, 'users', uid));
          if (snap.exists()) {
            userCache.set(uid, snap.data());
          } else {
            userCache.set(uid, { name: 'Cosmos User', headline: 'Builder' });
          }
        } catch (e) {
          console.error("Error fetching user profile:", uid, e);
          userCache.set(uid, { name: 'Cosmos User', headline: 'Builder' });
        }
      }));
    }

    // Sort by last message time
    list.sort((a, b) => {
      const timeA = a.lastMessageTime?.toDate ? a.lastMessageTime.toDate().getTime() : 0;
      const timeB = b.lastMessageTime?.toDate ? b.lastMessageTime.toDate().getTime() : 0;
      return timeB - timeA;
    });

    const renderInbox = (filteredList) => {
      const listContainer = outlet.querySelector('#convo-list');
      if (listContainer) {
        listContainer.innerHTML = renderConvoList(filteredList, user.uid);
        attachConvoListeners(outlet);
      }
    };

    renderInbox(list);

    // Search filter
    const searchInput = outlet.querySelector('#convo-search');
    if (searchInput) {
      const freshSearchInput = searchInput.cloneNode(true);
      searchInput.replaceWith(freshSearchInput);
      freshSearchInput.addEventListener('input', () => {
        const queryVal = freshSearchInput.value.toLowerCase();
        const filtered = list.filter(item => {
          const profile = userCache.get(item.otherId) || {};
          const name = (profile.name || '').toLowerCase();
          const msg = (item.lastMessage || '').toLowerCase();
          return name.includes(queryVal) || msg.includes(queryVal);
        });
        renderInbox(filtered);
      });
    }
  }, (error) => {
    console.error('[Cosmos Conversations] error:', error);
    const listContainer = outlet.querySelector('#convo-list');
    if (listContainer) {
      listContainer.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load conversations: ${error.message}</div>`;
    }
  });
}

function renderConvoList(connections, currentUserId) {
  if (!connections.length) {
    return `
      <div class="empty-state">
        <div class="empty-state-icon">💬</div>
        <h3 class="empty-state-title">No Conversations</h3>
        <p class="empty-state-desc">Start connecting with people to begin meaningful conversations.</p>
      </div>
    `;
  }

  const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#db2777,#f472b6)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#d97706,#fbbf24)'];

  return connections.map((c, i) => {
    const profile = userCache.get(c.otherId) || {};
    const name = profile.name || 'Unknown User';
    const avatarUrl = profile.avatarUrl || '';
    const initials = name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) || 'U';

    const lastMsgTime = formatLastMessageTime(c.lastMessageTime);
    const label = c.labels[0] || '';
    const labelColorClass = getLabelColorClass(label);
    const hasPhoto = !!avatarUrl;

    return `
      <div class="conversation-item anim-fade-up" data-id="${c.id}" style="animation-delay:${i * 0.05}s;">
        <div style="position:relative;">
          <div class="avatar" style="${hasPhoto ? '' : 'background:' + avatarColors[i % avatarColors.length]}">
            ${hasPhoto ? `<img src="${avatarUrl}" alt="${name}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />` : initials}
          </div>
        </div>
        <div class="conversation-info">
          <div class="conversation-name">
            ${name}
            ${label ? `<span class="badge ${labelColorClass}" style="font-size:0.62rem;margin-left:0.35rem;">${label}</span>` : ''}
          </div>
          <div class="conversation-preview">${c.lastMessage}</div>
        </div>
        <div class="conversation-meta">
          <div class="conversation-time">${lastMsgTime}</div>
          ${c.unreadCount > 0 ? `<div class="conversation-unread">${c.unreadCount}</div>` : ''}
        </div>
      </div>
    `;
  }).join('');
}

function attachConvoListeners(outlet) {
  outlet.querySelectorAll('.conversation-item').forEach(item => {
    item.addEventListener('click', () => {
      const id = item.dataset.id;
      window.location.hash = `#/conversations/chat/${id}`;
    });
  });
}

async function renderChatDetail(outlet, chatId) {
  const user = auth.currentUser;
  if (!user) return;

  cleanupListeners();

  const members = chatId.split('_');
  const otherId = members.find(id => id !== user.uid) || '';

  let otherProfile = userCache.get(otherId);
  if (!otherProfile) {
    otherProfile = await getOrFetchUserProfile(otherId) || { name: 'Cosmos User', headline: 'Builder' };
  }

  const name = otherProfile.name || 'Unknown User';
  const headline = otherProfile.headline || otherProfile.role || 'Cosmos Member';
  const avatarUrl = otherProfile.avatarUrl || '';
  const initials = name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) || 'U';

  const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#db2777,#f472b6)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#d97706,#fbbf24)'];
  const colorIdx = otherId.charCodeAt(0) || 0;
  const hasPhoto = !!avatarUrl;

  outlet.innerHTML = `
    <div class="chat-view">
      <div class="chat-header">
        <button class="chat-back-btn" id="chat-back">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <div class="avatar avatar-sm" style="${hasPhoto ? '' : 'background:' + avatarColors[colorIdx % avatarColors.length]}">
          ${hasPhoto ? `<img src="${avatarUrl}" alt="${name}" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />` : initials}
        </div>
        <div>
          <div style="font-weight:600;font-size:0.92rem;">${name}</div>
          <div style="font-size:0.72rem;color:var(--text-muted);">${headline}</div>
        </div>
      </div>
      <div class="chat-messages" id="chat-messages">
        <div class="loading-spinner" style="margin:2rem auto; display:block;"></div>
      </div>
      <div class="chat-input-area">
        <input class="chat-input" type="text" id="chat-input" placeholder="Type a message..." autocomplete="off" />
        <button class="chat-send-btn" id="chat-send">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
        </button>
      </div>
    </div>
  `;

  // Back button
  outlet.querySelector('#chat-back')?.addEventListener('click', () => {
    window.location.hash = '#/conversations';
  });

  // Mark as read
  try {
    const updateObj = {};
    updateObj[`unreadCountMap.${user.uid}`] = 0;
    await updateDoc(doc(db, 'connections', chatId), updateObj);
  } catch (e) {
    console.error('[Cosmos Conversations] Error marking messages read:', e);
  }

  // Subscribe to messages
  const messagesQuery = query(
    collection(db, 'connections', chatId, 'messages'),
    orderBy('timestamp', 'asc')
  );

  const messagesContainer = outlet.querySelector('#chat-messages');

  unsubMessages = onSnapshot(messagesQuery, (snapshot) => {
    const messages = [];
    snapshot.forEach(docSnap => {
      const data = docSnap.data();
      messages.push({
        id: docSnap.id,
        senderId: data.senderId,
        text: data.text || '',
        timestamp: data.timestamp,
        type: data.type || 'TEXT',
        isDeleted: data.isDeleted || false
      });
    });

    if (messagesContainer) {
      if (messages.length === 0) {
        messagesContainer.innerHTML = `
          <div style="text-align:center;color:var(--text-muted);font-size:0.85rem;padding:2rem;">
            Connection established! Say hello to ${name}.
          </div>
        `;
      } else {
        messagesContainer.innerHTML = messages.map(m => {
          if (m.isDeleted) {
            return `
              <div class="chat-bubble chat-bubble-received" style="font-style: italic; color: var(--text-muted);">
                This message was deleted
              </div>
            `;
          }
          if (m.type === 'AI_SUMMARY' || m.senderId === 'system') {
            return `
              <div class="ai-summary anim-fade-up">
                <div class="ai-summary-header">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="10"/></svg>
                  AI Meeting Summary
                </div>
                <div class="ai-summary-text">${m.text}</div>
              </div>
            `;
          }
          const isOwn = m.senderId === user.uid;
          const bubbleTime = formatMessageTime(m.timestamp);

          return `
            <div class="chat-bubble ${isOwn ? 'chat-bubble-sent' : 'chat-bubble-received'}">
              ${m.text}
              <div class="chat-bubble-time">${bubbleTime}</div>
            </div>
          `;
        }).join('');
      }
      messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
  }, (error) => {
    console.error('[Cosmos Conversations] Messages error:', error);
    if (messagesContainer) {
      messagesContainer.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load messages: ${error.message}</div>`;
    }
  });

  const input = outlet.querySelector('#chat-input');
  const sendBtn = outlet.querySelector('#chat-send');

  const sendMessage = async () => {
    const text = input.value.trim();
    if (!text) return;

    input.value = '';

    try {
      const messageDoc = {
        senderId: user.uid,
        text: text,
        timestamp: serverTimestamp(),
        type: 'TEXT',
        isDeleted: false
      };

      await addDoc(collection(db, 'connections', chatId, 'messages'), messageDoc);

      const updateMap = {
        lastMessage: text,
        lastMessageTime: serverTimestamp()
      };
      updateMap[`unreadCountMap.${otherId}`] = increment(1);
      updateMap[`unreadCountMap.${user.uid}`] = 0;

      await updateDoc(doc(db, 'connections', chatId), updateMap);
    } catch (e) {
      console.error('[Cosmos Conversations] Error sending message:', e);
      showToast('Failed to send message', 'error');
    }
  };

  sendBtn?.addEventListener('click', sendMessage);
  input?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') sendMessage();
  });
}

