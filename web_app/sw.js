/* ============================================================
   Cosmos PWA — Service Worker
   Cache-first for static assets, network-first for API calls
   ============================================================ */

const CACHE_NAME = 'cosmos-pwa-v11';
const STATIC_ASSETS = [
  '/web_app/index.html',
  '/web_app/css/styles.css',
  '/web_app/js/app.js',
  '/web_app/js/router.js',
  '/web_app/js/firebase-config.js',
  '/web_app/js/pages/auth.js',
  '/web_app/js/pages/connect.js',
  '/web_app/js/pages/events.js',
  '/web_app/js/pages/conversations.js',
  '/web_app/js/pages/communities.js',
  '/web_app/js/pages/social.js',
  '/web_app/js/pages/profile.js',
  '/web_app/js/pages/notifications.js',
  '/web_app/js/pages/settings.js',
  '/web_app/js/pages/edit-profile.js',
  '/web_app/js/pages/help-support.js',
  '/web_app/manifest.json',
  '/web_app/icons/icon-192.png',
  '/web_app/icons/icon-512.png',
  '/web_app/icons/logo.webp',
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
  if (url.pathname.startsWith('/web_app/')) {
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
        return caches.match('/web_app/index.html');
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
