/* ===================================================
   COSMOS WEBSITE — APP.JS
   Interactive animations, scroll effects, and UX
   =================================================== */

'use strict';

// ===================== NAVBAR SCROLL =====================
const navbar = document.getElementById('navbar');
if (navbar) {
  window.addEventListener('scroll', () => {
    navbar.classList.toggle('scrolled', window.scrollY > 40);
  }, { passive: true });
}

// ===================== HAMBURGER MENU =====================
const hamburger = document.getElementById('hamburger');
const navLinks  = document.getElementById('nav-links');
if (hamburger && navLinks) {
  hamburger.addEventListener('click', () => {
    hamburger.classList.toggle('active');
    navLinks.classList.toggle('open');
  });
  // Close on link click
  navLinks.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', () => {
      hamburger.classList.remove('active');
      navLinks.classList.remove('open');
    });
  });
}

// ===================== STAR FIELD =====================
function createStars() {
  const starsContainer = document.getElementById('stars');
  if (!starsContainer) return;

  const count = 120;
  for (let i = 0; i < count; i++) {
    const star = document.createElement('div');
    star.classList.add('star');

    const size    = Math.random() * 2 + 0.5;
    const x       = Math.random() * 100;
    const y       = Math.random() * 100;
    const dur     = (Math.random() * 4 + 2).toFixed(1) + 's';
    const delay   = (Math.random() * 5).toFixed(1) + 's';
    const minOp   = (Math.random() * 0.1).toFixed(2);
    const maxOp   = (Math.random() * 0.7 + 0.3).toFixed(2);

    star.style.cssText = `
      width: ${size}px;
      height: ${size}px;
      top: ${y}%;
      left: ${x}%;
      --dur: ${dur};
      --delay: ${delay};
      --min-op: ${minOp};
      --max-op: ${maxOp};
    `;
    starsContainer.appendChild(star);
  }
}
createStars();

// ===================== SCROLL ANIMATIONS =====================
function initScrollAnimations() {
  const elements = document.querySelectorAll('[data-animate]');
  if (!elements.length) return;

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const el    = entry.target;
        const delay = parseInt(el.dataset.delay || 0);
        setTimeout(() => {
          el.classList.add('animated');
          el.style.opacity = '';
        }, delay);
        observer.unobserve(el);
      }
    });
  }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });

  elements.forEach(el => observer.observe(el));
}
initScrollAnimations();

// ===================== COUNTER ANIMATION =====================
function animateCounter(el, target, duration = 1800) {
  let start     = null;
  const startVal = 0;

  function step(timestamp) {
    if (!start) start = timestamp;
    const progress = Math.min((timestamp - start) / duration, 1);
    const eased    = 1 - Math.pow(1 - progress, 3); // ease out cubic
    const current  = Math.floor(eased * target);
    el.textContent = current.toLocaleString();
    if (progress < 1) requestAnimationFrame(step);
    else el.textContent = target.toLocaleString();
  }
  requestAnimationFrame(step);
}

function initCounters() {
  const counters = document.querySelectorAll('[data-count]');
  if (!counters.length) return;

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        animateCounter(entry.target, parseInt(entry.target.dataset.count));
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.5 });

  counters.forEach(counter => observer.observe(counter));
}
initCounters();

// ===================== WAITLIST FORM =====================
function handleWaitlist(e) {
  e.preventDefault();
  const btn     = document.getElementById('waitlist-submit-btn');
  const form    = document.getElementById('waitlist-form');
  const success = document.getElementById('form-success');

  if (!btn || !form) return;

  // Loading state
  btn.disabled = true;
  btn.innerHTML = `
    <svg class="spin-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
    </svg>
    Joining...
  `;
  btn.style.opacity = '0.7';

  // Simulate async (replace with actual API call)
  setTimeout(() => {
    form.style.display = 'none';
    if (success) {
      success.style.display = 'block';
      success.style.animation = 'fadeInUp 0.5s ease both';
    }
  }, 1500);
}
window.handleWaitlist = handleWaitlist;

// ===================== SMOOTH ACTIVE SECTION TRACKING =====================
function initActiveSectionTracking() {
  const sections  = document.querySelectorAll('.legal-section');
  const tocLinks  = document.querySelectorAll('.toc-link');
  if (!sections.length || !tocLinks.length) return;

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const id = entry.target.id;
        tocLinks.forEach(link => {
          link.classList.toggle('active', link.getAttribute('href') === `#${id}`);
        });
      }
    });
  }, {
    rootMargin: `-${72 + 24}px 0px -60% 0px`,
    threshold: 0
  });

  sections.forEach(section => observer.observe(section));
}
initActiveSectionTracking();

// ===================== LIVE COUNTDOWN TIMER =====================
function initEventTimer() {
  const timerEl = document.querySelector('.event-timer strong');
  if (!timerEl) return;

  function tick() {
    const now     = new Date();
    const target  = new Date(now);
    target.setDate(target.getDate() + 1);
    target.setHours(18, 0, 0, 0);

    const diff = target - now;
    if (diff <= 0) return;

    const h = Math.floor(diff / 3600000).toString().padStart(2, '0');
    const m = Math.floor((diff % 3600000) / 60000).toString().padStart(2, '0');
    const s = Math.floor((diff % 60000) / 1000).toString().padStart(2, '0');
    timerEl.textContent = `${h}:${m}:${s}`;
  }

  tick();
  setInterval(tick, 1000);
}
initEventTimer();

// ===================== SWIPE BUTTON DEMO =====================
function initSwipeDemo() {
  const yesBtn = document.querySelector('.swipe-yes');
  const noBtn  = document.querySelector('.swipe-no');
  const card   = document.querySelector('.swipe-card');
  if (!yesBtn || !noBtn || !card) return;

  yesBtn.addEventListener('click', () => {
    card.style.transform = 'translateX(60px) rotate(10deg)';
    card.style.opacity   = '0';
    card.style.transition = 'all 0.4s ease';
    setTimeout(() => {
      card.style.transform = '';
      card.style.opacity   = '1';
      card.style.transition = 'all 0.5s ease';
    }, 600);
  });

  noBtn.addEventListener('click', () => {
    card.style.transform = 'translateX(-60px) rotate(-10deg)';
    card.style.opacity   = '0';
    card.style.transition = 'all 0.4s ease';
    setTimeout(() => {
      card.style.transform = '';
      card.style.opacity   = '1';
      card.style.transition = 'all 0.5s ease';
    }, 600);
  });
}
initSwipeDemo();

// ===================== SMOOTH SCROLL FOR ANCHOR LINKS =====================
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
  anchor.addEventListener('click', function(e) {
    const targetId = this.getAttribute('href');
    if (targetId === '#') return;
    const target = document.querySelector(targetId);
    if (!target) return;
    e.preventDefault();
    const navH = parseInt(getComputedStyle(document.documentElement).getPropertyValue('--nav-h')) || 72;
    window.scrollTo({
      top: target.getBoundingClientRect().top + window.scrollY - navH - 16,
      behavior: 'smooth'
    });
  });
});

// ===================== SPIN ICON CSS INJECT =====================
const spinStyle = document.createElement('style');
spinStyle.textContent = `
  .spin-icon {
    animation: spinAnim 0.8s linear infinite;
  }
  @keyframes spinAnim {
    from { transform: rotate(0deg); }
    to   { transform: rotate(360deg); }
  }
`;
document.head.appendChild(spinStyle);

// ===================== PARALLAX ORBS (subtle) =====================
function initParallax() {
  const orbs = document.querySelectorAll('.orb-1, .orb-2, .orb-3');
  if (!orbs.length) return;

  window.addEventListener('mousemove', (e) => {
    const x = (e.clientX / window.innerWidth  - 0.5) * 20;
    const y = (e.clientY / window.innerHeight - 0.5) * 20;
    orbs.forEach((orb, i) => {
      const factor = (i + 1) * 0.5;
      orb.style.transform = `translate(${x * factor}px, ${y * factor}px)`;
    });
  }, { passive: true });
}
initParallax();

console.log('🌌 Cosmos — Where meaningful connections begin.');
