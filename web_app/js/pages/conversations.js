/* ============================================================
   Cosmos PWA — Conversations Page (CRM Chat)
   ============================================================ */

import { showToast } from '../app.js';

const demoConversations = [
  { id: '1', name: 'Sarah Chen', initials: 'SC', role: 'Founder @ BuildSpark AI', lastMessage: 'That sounds great! Let me share the deck with you tomorrow.', time: '2m ago', unread: 2, label: 'Potential Partner', labelColor: 'badge-green', status: 'online' },
  { id: '2', name: 'Marcus Rivera', initials: 'MR', role: 'Angel Investor @ Horizon', lastMessage: 'I'd love to hear more about your traction numbers.', time: '1h ago', unread: 0, label: 'Warm Intro', labelColor: 'badge-amber', status: 'offline' },
  { id: '3', name: 'Aisha Patel', initials: 'AP', role: 'Product Lead @ Notion', lastMessage: 'Great meeting today! Here are the key takeaways...', time: '3h ago', unread: 1, label: 'Follow Up', labelColor: 'badge-purple', status: 'online' },
  { id: '4', name: 'James Okafor', initials: 'JO', role: 'CTO @ DataVerse', lastMessage: 'The architecture looks solid. Let's discuss scaling.', time: 'Yesterday', unread: 0, label: 'Active', labelColor: 'badge-blue', status: 'offline' },
  { id: '5', name: 'Elena Volkov', initials: 'EV', role: 'Operator @ Sequoia', lastMessage: 'I connected you with Lisa — check your notifications!', time: 'Yesterday', unread: 0, label: 'Important', labelColor: 'badge-purple', status: 'offline' },
];

const demoChatMessages = [
  { sender: 'them', text: 'Hey! Great to connect at the Founders Speed Network event 🔥', time: '2:30 PM' },
  { sender: 'me', text: 'Absolutely! Your work on AI-powered sales tools is really interesting.', time: '2:32 PM' },
  { sender: 'them', text: 'Thanks! I saw your background in data infrastructure — that\'s exactly what we need.', time: '2:33 PM' },
  { sender: 'ai', text: '🤖 AI Summary: Sarah is building an AI sales tool and looking for a technical co-founder with data infrastructure experience. Key alignment: both interested in B2B SaaS and scalable architectures.', time: '2:33 PM' },
  { sender: 'me', text: 'I\'d love to learn more about your tech stack. Are you using any specific ML frameworks?', time: '2:35 PM' },
  { sender: 'them', text: 'We\'re on PyTorch with a custom transformer architecture. Would love to walk you through it!', time: '2:36 PM' },
  { sender: 'them', text: 'That sounds great! Let me share the deck with you tomorrow.', time: '2:38 PM' },
];

let chatViewOpen = false;
let activeChatId = null;

export async function renderConversations(outlet, path) {
  // Check if we're viewing a specific chat
  const parts = path.split('/').filter(Boolean);
  if (parts.length > 1 && parts[1] === 'chat') {
    activeChatId = parts[2] || '1';
    renderChatDetail(outlet, activeChatId);
    return;
  }

  chatViewOpen = false;
  activeChatId = null;

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
        ${renderConvoList(demoConversations)}
      </div>
    </div>
  `;

  // Search filter
  const searchInput = outlet.querySelector('#convo-search');
  searchInput?.addEventListener('input', () => {
    const q = searchInput.value.toLowerCase();
    const filtered = demoConversations.filter(c =>
      c.name.toLowerCase().includes(q) || c.lastMessage.toLowerCase().includes(q)
    );
    outlet.querySelector('#convo-list').innerHTML = renderConvoList(filtered);
    attachConvoListeners(outlet);
  });

  attachConvoListeners(outlet);
}

function renderConvoList(conversations) {
  if (!conversations.length) {
    return `
      <div class="empty-state">
        <div class="empty-state-icon">💬</div>
        <h3 class="empty-state-title">No Conversations</h3>
        <p class="empty-state-desc">Start connecting with people to begin meaningful conversations.</p>
      </div>
    `;
  }

  const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#db2777,#f472b6)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#d97706,#fbbf24)'];

  return conversations.map((c, i) => `
    <div class="conversation-item anim-fade-up" data-id="${c.id}" style="animation-delay:${i * 0.05}s;">
      <div style="position:relative;">
        <div class="avatar" style="background:${avatarColors[i % avatarColors.length]};">${c.initials}</div>
        ${c.status === 'online' ? '<div style="position:absolute;bottom:0;right:0;width:10px;height:10px;border-radius:50%;background:var(--green);border:2px solid var(--bg-primary);"></div>' : ''}
      </div>
      <div class="conversation-info">
        <div class="conversation-name">
          ${c.name}
          <span class="badge ${c.labelColor}" style="font-size:0.62rem;">${c.label}</span>
        </div>
        <div class="conversation-preview">${c.lastMessage}</div>
      </div>
      <div class="conversation-meta">
        <div class="conversation-time">${c.time}</div>
        ${c.unread > 0 ? `<div class="conversation-unread">${c.unread}</div>` : ''}
      </div>
    </div>
  `).join('');
}

function attachConvoListeners(outlet) {
  outlet.querySelectorAll('.conversation-item').forEach(item => {
    item.addEventListener('click', () => {
      const id = item.dataset.id;
      renderChatDetail(outlet, id);
    });
  });
}

function renderChatDetail(outlet, chatId) {
  const convo = demoConversations.find(c => c.id === chatId) || demoConversations[0];
  chatViewOpen = true;
  activeChatId = chatId;
  const avatarColors = ['linear-gradient(135deg,#7c3aed,#a78bfa)', 'linear-gradient(135deg,#2563eb,#60a5fa)', 'linear-gradient(135deg,#db2777,#f472b6)', 'linear-gradient(135deg,#059669,#34d399)', 'linear-gradient(135deg,#d97706,#fbbf24)'];
  const idx = demoConversations.indexOf(convo);

  outlet.innerHTML = `
    <div class="chat-view">
      <div class="chat-header">
        <button class="chat-back-btn" id="chat-back">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <div class="avatar avatar-sm" style="background:${avatarColors[idx % avatarColors.length]};">${convo.initials}</div>
        <div>
          <div style="font-weight:600;font-size:0.92rem;">${convo.name}</div>
          <div style="font-size:0.72rem;color:var(--text-muted);">${convo.role}</div>
        </div>
        <div style="margin-left:auto;">
          <span class="badge ${convo.labelColor}">${convo.label}</span>
        </div>
      </div>
      <div class="chat-messages" id="chat-messages">
        ${demoChatMessages.map(m => {
          if (m.sender === 'ai') {
            return `
              <div class="ai-summary anim-fade-up">
                <div class="ai-summary-header">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="10"/></svg>
                  AI Meeting Summary
                </div>
                <div class="ai-summary-text">${m.text.replace('🤖 AI Summary: ', '')}</div>
              </div>
            `;
          }
          return `
            <div class="chat-bubble ${m.sender === 'me' ? 'chat-bubble-sent' : 'chat-bubble-received'}">
              ${m.text}
              <div class="chat-bubble-time">${m.time}</div>
            </div>
          `;
        }).join('')}
      </div>
      <div class="chat-input-area">
        <input class="chat-input" type="text" id="chat-input" placeholder="Type a message..." autocomplete="off" />
        <button class="chat-send-btn" id="chat-send">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
        </button>
      </div>
    </div>
  `;

  // Scroll to bottom
  const messages = outlet.querySelector('#chat-messages');
  if (messages) messages.scrollTop = messages.scrollHeight;

  // Back button
  outlet.querySelector('#chat-back')?.addEventListener('click', () => {
    renderConversations(outlet, '/conversations');
  });

  // Send message
  const input = outlet.querySelector('#chat-input');
  const sendBtn = outlet.querySelector('#chat-send');

  const sendMessage = () => {
    const text = input.value.trim();
    if (!text) return;
    
    const bubble = document.createElement('div');
    bubble.className = 'chat-bubble chat-bubble-sent';
    const now = new Date();
    const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    bubble.innerHTML = `${text}<div class="chat-bubble-time">${timeStr}</div>`;
    messages.appendChild(bubble);
    messages.scrollTop = messages.scrollHeight;
    input.value = '';
  };

  sendBtn?.addEventListener('click', sendMessage);
  input?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') sendMessage();
  });
}
