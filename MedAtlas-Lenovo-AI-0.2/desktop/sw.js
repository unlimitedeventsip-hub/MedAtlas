const CACHE = 'medatlas-companion-v2';
const SHELL = ['./','./index.html','./styles.css','./app.js','./stroke-format.mjs','./manifest.webmanifest','./icon.svg'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)));
  self.skipWaiting();
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys =>
    Promise.all(keys.filter(key => key.startsWith('medatlas-companion-') && key !== CACHE)
      .map(key => caches.delete(key)))));
  self.clients.claim();
});
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  // Never cache external URLs, private APIs or user documents.
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (!SHELL.some(entry => new URL(entry,self.registration.scope).pathname === url.pathname)) return;
  event.respondWith(caches.match(event.request).then(saved =>
    saved || fetch(event.request)));
});
