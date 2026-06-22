/* ============================================================
   Cosmos PWA — Service Worker
   Cache-first for static assets, network-first for API calls
   ============================================================ */

const CACHE_NAME = 'cosmos-pwa-v1';
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

// Fetch — cache-first for static, network-first for API
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Network-first for Firebase / API calls
  if (
    url.hostname.includes('firestore.googleapis.com') ||
    url.hostname.includes('identitytoolkit.googleapis.com') ||
    url.hostname.includes('securetoken.googleapis.com') ||
    url.hostname.includes('firebasestorage.googleapis.com')
  ) {
    event.respondWith(
      fetch(request).catch(() => caches.match(request))
    );
    return;
  }

  // Cache-first for static assets
  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;
      return fetch(request).then((response) => {
        // Cache successful GET responses
        if (request.method === 'GET' && response.status === 200) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
        }
        return response;
      });
    }).catch(() => {
      // Offline fallback for navigation requests
      if (request.mode === 'navigate') {
        return caches.match('/web_app/index.html');
      }
    })
  );
});
