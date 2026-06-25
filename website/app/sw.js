/* ============================================================
   Cosmos PWA — Service Worker
   Cache-first for static assets, network-first for API calls
   ============================================================ */

const CACHE_NAME = 'cosmos-pwa-v11';
const STATIC_ASSETS = [
  '/app/index.html',
  '/app/css/styles.css',
  '/app/js/app.js',
  '/app/js/router.js',
  '/app/js/firebase-config.js',
  '/app/js/pages/auth.js',
  '/app/js/pages/connect.js',
  '/app/js/pages/events.js',
  '/app/js/pages/conversations.js',
  '/app/js/pages/communities.js',
  '/app/js/pages/social.js',
  '/app/js/pages/profile.js',
  '/app/js/pages/notifications.js',
  '/app/js/pages/settings.js',
  '/app/js/pages/edit-profile.js',
  '/app/js/pages/help-support.js',
  '/app/js/pages/messenger.js',
  '/app/js/pages/orbits.js',
  '/app/js/pages/organize.js',
  '/app/manifest.json',
  '/app/icons/icon-192.png',
  '/app/icons/icon-512.png',
  '/app/icons/logo.webp',
];

// Install — cache static shell
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS).catch(() => {
        // Gracefully handle individual fetch failures during install
        return Promise.allSettled(
          STATIC_ASSETS.map((url) => cache.add(url).catch(() => {}))
        );
      });
    })
  );
  self.skipWaiting();
});

// Activate — clean old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))
      )
    )
  );
  self.clients.claim();
});

// Fetch — network-first for API and local assets, falling back to cache
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // 1. Firebase / API calls — let the browser handle these directly
  if (
    url.hostname.includes('firestore.googleapis.com') ||
    url.hostname.includes('identitytoolkit.googleapis.com') ||
    url.hostname.includes('securetoken.googleapis.com') ||
    url.hostname.includes('firebasestorage.googleapis.com')
  ) {
    return;
  }

  // 2. Local app assets (HTML, CSS, JS, manifest, icons)
  if (url.pathname.startsWith('/app/')) {
    event.respondWith(
      fetch(request)
        .then((response) => {
          if (request.method === 'GET' && response.status === 200) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
          }
          return response;
        })
        .catch(() => caches.match(request))
    );
    return;
  }

  // 3. Fallback cache-first for other external assets (fonts, CDNs, etc.)
  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;
      return fetch(request).then((response) => {
        if (request.method === 'GET' && response.status === 200) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
        }
        return response;
      });
    }).catch(() => {
      if (request.mode === 'navigate') {
        return caches.match('/app/index.html');
      }
    })
  );
});

// Update hook — allow manual update trigger
self.addEventListener('message', (event) => {
  if (event.data && event.data.action === 'skipWaiting') {
    self.skipWaiting();
  }
});
