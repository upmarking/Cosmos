/* ============================================================
   Cosmos PWA — Messenger Page (CRM Chat)
   Maps to the 'M' in COSMOS navigation
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

function highlightText(text, query) {
  if (!query || !query.trim()) return text;
  const escapedQuery = query.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
  const reg = new RegExp(`(${escapedQuery})`, 'gi');
  return text.replace(reg, '<mark style="background:rgba(255,213,79,0.35);color:inherit;border-radius:2px;padding:0 2px;">$1</mark>');
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

export async function renderMessenger(outlet, path) {
  const user = auth.currentUser;
  if (!user) return;

  // Check if we're viewing a specific chat
  const parts = path.split('/').filter(Boolean);
  if (parts.length > 1 && parts[1] === 'chat') {
    const activeChatId = parts[2];
    renderChatDetail(outlet, activeChatId);
    return () => cleanupListeners();
  }

  cleanupListeners();

  outlet.innerHTML = `
    <div class="conversations-page page">
      <div class="page-header">
        <h1 class="page-title">Messenger</h1>
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

  let allConnections = [];
  let inboxSearchQuery = '';

  const renderFilteredInbox = () => {
    const listContainer = outlet.querySelector('#convo-list');
    if (!listContainer) return;

    const filtered = allConnections.filter(item => {
      const profile = userCache.get(item.otherId) || {};
      const name = (profile.name || '').toLowerCase();
      const msg = (item.lastMessage || '').toLowerCase();
      return name.includes(inboxSearchQuery) || msg.includes(inboxSearchQuery);
    });

    listContainer.innerHTML = renderConvoList(filtered, user.uid);
    attachConvoListeners(outlet);
  };

  // Wire search input listener once
  const searchInput = outlet.querySelector('#convo-search');
  searchInput?.addEventListener('input', (e) => {
    inboxSearchQuery = e.target.value.toLowerCase();
    renderFilteredInbox();
  });

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

    allConnections = list;
    renderFilteredInbox();
  }, (error) => {
    console.error('[Cosmos Messenger] error:', error);
    const listContainer = outlet.querySelector('#convo-list');
    if (listContainer) {
      listContainer.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load conversations: ${error.message}</div>`;
    }
  });

  return () => cleanupListeners();
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
      window.location.hash = `#/messenger/chat/${id}`;
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
        
        <div style="flex:1; display:flex; align-items:center; min-width:0; margin-left:0.5rem;" id="chat-title-container">
          <div id="chat-title-info" style="min-width:0; flex:1;">
            <div style="font-weight:600;font-size:0.92rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${name}</div>
            <div style="font-size:0.72rem;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${headline}</div>
          </div>
          <div id="chat-search-input-wrap" class="hidden" style="width:100%;">
            <input type="text" id="chat-msg-search" placeholder="Search messages..." style="width:100%; background:rgba(255,255,255,0.06); border:1px solid var(--border); border-radius:12px; padding:6px 12px; color:var(--text-primary); outline:none; font-size:0.85rem;" />
          </div>
        </div>

        <button class="chat-search-btn" id="chat-search-trigger" style="color:var(--text-secondary);padding:6px;border-radius:8px;display:flex;align-items:center;justify-content:center;margin-left:0.5rem;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        </button>
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
    window.location.hash = '#/messenger';
  });

  // Mark connection as read initially
  try {
    const updateObj = {};
    updateObj[`unreadCountMap.${user.uid}`] = 0;
    await updateDoc(doc(db, 'connections', chatId), updateObj);
  } catch (e) {
    console.error('[Cosmos Messenger] Error marking messages read:', e);
  }

  let allMessages = [];
  let messageSearchQuery = '';

  const messagesContainer = outlet.querySelector('#chat-messages');

  const renderMessagesList = () => {
    if (!messagesContainer) return;

    let displayMessages = allMessages;
    if (messageSearchQuery.trim()) {
      const q = messageSearchQuery.toLowerCase();
      displayMessages = allMessages.filter(m => !m.isDeleted && m.text.toLowerCase().includes(q));
    }

    if (displayMessages.length === 0) {
      if (messageSearchQuery.trim()) {
        messagesContainer.innerHTML = `
          <div style="text-align:center;color:var(--text-muted);font-size:0.85rem;padding:3rem 1rem;">
            <div style="font-size:1.5rem;margin-bottom:0.5rem;">🔍</div>
            <div>No messages matching "${messageSearchQuery}"</div>
          </div>
        `;
      } else {
        messagesContainer.innerHTML = `
          <div style="text-align:center;color:var(--text-muted);font-size:0.85rem;padding:2rem;">
            Connection established! Say hello to ${name}.
          </div>
        `;
      }
      return;
    }

    messagesContainer.innerHTML = displayMessages.map(m => {
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
            <div class="ai-summary-text">${highlightText(m.text, messageSearchQuery)}</div>
          </div>
        `;
      }
      const isOwn = m.senderId === user.uid;
      const bubbleTime = formatMessageTime(m.timestamp);

      return `
        <div class="chat-bubble ${isOwn ? 'chat-bubble-sent' : 'chat-bubble-received'}">
          ${highlightText(m.text, messageSearchQuery)}
          <div class="chat-bubble-time">${bubbleTime}</div>
        </div>
      `;
    }).join('');

    // Only scroll to bottom if user is not actively searching
    if (!messageSearchQuery.trim()) {
      messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
  };

  // Subscribe to messages
  const messagesQuery = query(
    collection(db, 'connections', chatId, 'messages'),
    orderBy('timestamp', 'asc')
  );

  unsubMessages = onSnapshot(messagesQuery, async (snapshot) => {
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

    allMessages = messages;
    renderMessagesList();

    // Check if there are new incoming messages to mark as read immediately
    const hasNewIncoming = snapshot.docChanges().some(change => 
      change.type === 'added' && 
      change.doc.data().senderId !== user.uid
    );

    if (hasNewIncoming) {
      try {
        const updateObj = {};
        updateObj[`unreadCountMap.${user.uid}`] = 0;
        await updateDoc(doc(db, 'connections', chatId), updateObj);
      } catch (e) {
        console.warn('[Cosmos Messenger] Quietly ignored mark-read update error:', e);
      }
    }
  }, (error) => {
    console.error('[Cosmos Messenger] Messages error:', error);
    if (messagesContainer) {
      messagesContainer.innerHTML = `<div style="text-align:center;color:var(--red);padding:2rem;">Failed to load messages: ${error.message}</div>`;
    }
  });

  // Message Search UI interactions
  const searchTrigger = outlet.querySelector('#chat-search-trigger');
  const titleInfo = outlet.querySelector('#chat-title-info');
  const searchInputWrap = outlet.querySelector('#chat-search-input-wrap');
  const searchInput = outlet.querySelector('#chat-msg-search');

  let isSearching = false;

  searchTrigger?.addEventListener('click', () => {
    isSearching = !isSearching;
    if (isSearching) {
      titleInfo.classList.add('hidden');
      searchInputWrap.classList.remove('hidden');
      searchInput.focus();
      searchTrigger.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>`;
    } else {
      titleInfo.classList.remove('hidden');
      searchInputWrap.classList.add('hidden');
      searchInput.value = '';
      messageSearchQuery = '';
      searchTrigger.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>`;
      renderMessagesList();
    }
  });

  searchInput?.addEventListener('input', () => {
    messageSearchQuery = searchInput.value;
    renderMessagesList();
  });

  // Sending message
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
      console.error('[Cosmos Messenger] Error sending message:', e);
      showToast('Failed to send message', 'error');
    }
  };

  sendBtn?.addEventListener('click', sendMessage);
  input?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') sendMessage();
  });
}
