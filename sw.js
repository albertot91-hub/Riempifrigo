/* Riempifrigo service worker: keeps the app working offline.
   App files: served from cache at once, refreshed in the background (updates show on the next launch).
   Google Fonts: cached the first time they load. */
const CACHE = "riempifrigo-v2";
const SHELL = ["./", "./index.html", "./manifest.webmanifest", "./icon-192.png", "./icon-512.png", "./icon-maskable.png"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", e => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);

  if (url.origin === location.origin){
    const key = req.mode === "navigate" ? "./index.html" : req;
    e.respondWith(
      caches.open(CACHE).then(async cache => {
        const cached = await cache.match(key, { ignoreSearch: true });
        const fresh = fetch(req).then(res => {
          if (res && res.ok) cache.put(key, res.clone());
          return res;
        }).catch(() => null);
        if (cached){ e.waitUntil(fresh); return cached; }
        const res = await fresh;
        return res || new Response("Riempifrigo è offline e non ha ancora salvato questa pagina.", { status: 503, headers: { "Content-Type": "text/plain; charset=utf-8" } });
      })
    );
    return;
  }

  if (url.hostname === "fonts.googleapis.com" || url.hostname === "fonts.gstatic.com"){
    e.respondWith(
      caches.open(CACHE).then(async cache => {
        const cached = await cache.match(req);
        if (cached) return cached;
        try {
          const res = await fetch(req);
          if (res && (res.ok || res.type === "opaque")) cache.put(req, res.clone());
          return res;
        } catch (err) {
          return new Response("", { status: 504 });
        }
      })
    );
  }
});
