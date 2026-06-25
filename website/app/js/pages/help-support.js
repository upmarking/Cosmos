/* ============================================================
   Cosmos PWA — Help & Support Page
   ============================================================ */

import { auth, db, collection, addDoc, serverTimestamp } from '../firebase-config.js';
import { showToast } from '../app.js';
import router from '../router.js';

const FAQS = [
  {
    question: 'Why is there a limit of 10 new connections per month?',
    answer: 'Cosmos prioritizes quality over quantity. By limiting new connections to 10 per month, we ensure that every interaction is intentional, structured, and leads to meaningful relationship building rather than mindless swiping.',
    category: 'Matchmaking & Connections',
  },
  {
    question: 'How does the AI matching logic work?',
    answer: 'Our recommendation engine analyzes your industry, role, interest tags, professional goals, and complementary needs (e.g., co-founders matching with investors or mentors matching with students) to prioritize the most relevant profiles for you.',
    category: 'Matchmaking & Connections',
  },
  {
    question: 'How do structured speed networking rounds work?',
    answer: 'Events are split into multiple 15-minute rounds. You will be matched with another participant based on common tags or goals. A countdown timer will show, and after the round, you can provide quick feedback and ratings.',
    category: 'Events & Networking',
  },
  {
    question: 'How do paid events and refunds work?',
    answer: 'Some premium events require an upfront fee. If you register, attend the scheduled rounds, and receive positive collaboration feedback, a refund may be triggered automatically to reward active participation.',
    category: 'Events & Networking',
  },
  {
    question: 'How are meeting AI summaries generated?',
    answer: 'If you record/transcript your meeting inside the platform, our AI models analyze the key discussion points, decisions, and action items, then automatically save a concise summary directly in your conversation history.',
    category: 'AI Summaries & CRM',
  },
  {
    question: 'Who can see my CRM labels and private goals?',
    answer: 'Your CRM notes, labels (like "Follow up needed" or "Warm intro requested"), and private relationship goals are 100% private to you. The other person cannot see them.',
    category: 'AI Summaries & CRM',
  },
  {
    question: 'How do I pause my account visibility?',
    answer: "Go to Settings → Control Center → Privacy, and toggle 'Profile Visibility' off. This stops you from appearing in matchmaking decks while keeping your existing chats and events active.",
    category: 'Account & Security',
  },
  {
    question: 'How can I block or report someone?',
    answer: "Go to the profile of the person you want to block or report, tap the three dots at the top right, and select 'Block' or 'Report'. You can also review your list of blocked users under Control Center.",
    category: 'Account & Security',
  },
];

const CATEGORIES = [
  'Technical Issue',
  'Matchmaking & Discovery',
  'Events & Rounds',
  'Billing & Premium Tiers',
  'General Feedback',
  'Other',
];

export async function renderHelpSupport(outlet) {
  const user = auth.currentUser;
  if (!user) {
    router.navigate('/auth');
    return;
  }

  let searchQuery = '';
  let expandedQuestion = null;
  let selectedCategory = CATEGORIES[0];
  let descriptionText = '';
  let isSubmitted = false;
  let isSubmitting = false;

  const render = () => {
    const filtered = searchQuery.trim()
      ? FAQS.filter((faq) =>
          faq.question.toLowerCase().includes(searchQuery.toLowerCase()) ||
          faq.answer.toLowerCase().includes(searchQuery.toLowerCase()) ||
          faq.category.toLowerCase().includes(searchQuery.toLowerCase())
        )
      : FAQS;

    const grouped = searchQuery.trim()
      ? null
      : filtered.reduce((acc, faq) => {
          if (!acc[faq.category]) acc[faq.category] = [];
          acc[faq.category].push(faq);
          return acc;
        }, {});

    outlet.innerHTML = `
      <div class="help-support-page page">
        <div class="sub-page-header">
          <button class="btn-back" id="btn-help-back" aria-label="Go back">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
          </button>
          <h1 class="page-title">Help & Support</h1>
        </div>

        <div class="help-intro">
          <h2>How can we help you?</h2>
          <p>Search our knowledge base or submit a support request below.</p>
        </div>

        <div class="help-search-wrap">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input class="form-input help-search-input" type="search" id="faq-search" placeholder="Search FAQs..." value="${escapeAttr(searchQuery)}" />
          ${searchQuery ? `<button type="button" class="help-search-clear" id="btn-clear-search">✕</button>` : ''}
        </div>

        <div class="help-faq-list" id="faq-list">
          ${renderFaqList(filtered, grouped, expandedQuestion)}
        </div>

        <div class="help-contact-section">
          <div class="settings-section-title">Contact Support</div>
          <div class="settings-card help-contact-card">
            ${isSubmitted ? renderSuccessView() : renderSupportForm(selectedCategory, descriptionText, isSubmitting)}
          </div>
        </div>
      </div>
    `;

    const backRoute = router.getBackRoute('/settings');
    outlet.querySelector('#btn-help-back').addEventListener('click', () => router.navigate(backRoute));

    const searchInput = outlet.querySelector('#faq-search');
    searchInput.addEventListener('input', (e) => {
      searchQuery = e.target.value;
      expandedQuestion = null;
      render();
    });

    const clearBtn = outlet.querySelector('#btn-clear-search');
    if (clearBtn) {
      clearBtn.addEventListener('click', () => {
        searchQuery = '';
        expandedQuestion = null;
        render();
      });
    }

    outlet.querySelectorAll('.faq-item').forEach((item) => {
      item.addEventListener('click', () => {
        const q = item.dataset.question;
        expandedQuestion = expandedQuestion === q ? null : q;
        render();
      });
    });

    if (!isSubmitted) {
      const categorySelect = outlet.querySelector('#support-category');
      const descInput = outlet.querySelector('#support-description');
      const submitBtn = outlet.querySelector('#btn-submit-support');

      categorySelect?.addEventListener('change', (e) => {
        selectedCategory = e.target.value;
      });
      descInput?.addEventListener('input', (e) => {
        descriptionText = e.target.value;
      });
      submitBtn?.addEventListener('click', submitTicket);
    } else {
      outlet.querySelector('#btn-submit-another')?.addEventListener('click', () => {
        isSubmitted = false;
        descriptionText = '';
        render();
      });
    }
  };

  const submitTicket = async () => {
    if (!descriptionText.trim()) {
      showToast('Please describe your query.', 'error');
      return;
    }

    isSubmitting = true;
    render();

    try {
      await addDoc(collection(db, 'support_tickets'), {
        userId: user.uid,
        category: selectedCategory,
        description: descriptionText.trim(),
        status: 'OPEN',
        timestamp: serverTimestamp(),
      });

      await addDoc(collection(db, 'notifications'), {
        userId: user.uid,
        type: 'COMMUNITY_ANNOUNCEMENT',
        title: 'Support Request Received 📥',
        body: `We received your request about '${selectedCategory}'. A representative will review it soon.`,
        timestamp: serverTimestamp(),
        isRead: false,
        actionId: `support_${Date.now()}`,
      });

      isSubmitted = true;
    } catch (err) {
      console.error('Support ticket failed:', err);
      showToast(err.message || 'Failed to submit request. Please try again.', 'error');
    } finally {
      isSubmitting = false;
      render();
    }
  };

  render();
}

function renderFaqList(filtered, grouped, expandedQuestion) {
  if (filtered.length === 0) {
    return `<div class="help-empty">No matching FAQ articles found.</div>`;
  }

  if (grouped) {
    return Object.entries(grouped).map(([category, faqs]) => `
      <div class="help-faq-group">
        <div class="settings-section-title">${category}</div>
        ${faqs.map((faq) => renderFaqItem(faq, expandedQuestion)).join('')}
      </div>
    `).join('');
  }

  return `
    <div class="help-faq-group">
      <div class="settings-section-title">Search Results (${filtered.length})</div>
      ${filtered.map((faq) => renderFaqItem(faq, expandedQuestion)).join('')}
    </div>
  `;
}

function renderFaqItem(faq, expandedQuestion) {
  const isOpen = expandedQuestion === faq.question;
  return `
    <div class="faq-item ${isOpen ? 'open' : ''}" data-question="${escapeAttr(faq.question)}">
      <div class="faq-question">
        <span>${faq.question}</span>
        <svg class="faq-chevron" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
      </div>
      ${isOpen ? `<div class="faq-answer">${faq.answer}</div>` : ''}
    </div>
  `;
}

function renderSupportForm(selectedCategory, descriptionText, isSubmitting) {
  return `
    <p class="help-form-intro">Describe your issue or feedback, and we will get back to you soon.</p>
    <div class="form-group">
      <label class="form-label" for="support-category">Category</label>
      <select class="form-input" id="support-category">
        ${CATEGORIES.map((cat) => `<option value="${cat}" ${cat === selectedCategory ? 'selected' : ''}>${cat}</option>`).join('')}
      </select>
    </div>
    <div class="form-group">
      <label class="form-label" for="support-description">Details / Explanation</label>
      <textarea class="form-input" id="support-description" rows="5" placeholder="Explain your issue in detail...">${escapeHtml(descriptionText)}</textarea>
    </div>
    <button type="button" class="btn btn-primary btn-full" id="btn-submit-support" ${isSubmitting ? 'disabled' : ''}>
      ${isSubmitting ? 'Submitting...' : 'Submit Request'}
    </button>
  `;
}

function renderSuccessView() {
  return `
    <div class="help-success">
      <div class="help-success-icon">✓</div>
      <h3>Request Submitted!</h3>
      <p>Your support request has been logged successfully. We've sent a confirmation notification, and a member of our team will reach out to you within 24 hours.</p>
      <button type="button" class="btn btn-outline btn-full" id="btn-submit-another">Submit Another Request</button>
    </div>
  `;
}

function escapeAttr(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;');
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}
