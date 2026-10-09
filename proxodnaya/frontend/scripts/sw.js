// Service worker «Проходной» (ADR-043). Версию и список файлов подставляет сборка (scripts/vite-pwa.ts).
// Оболочка приложения и все чанки кладутся в кэш при установке — пропуск, терминал и админка открываются без сети.
const VERSION = "__VERSION__";
const PRECACHE = __PRECACHE__;
const SHELL = `proxodnaya-shell-${VERSION}`;
const RUNTIME = "proxodnaya-runtime";
const FONT_HOSTS = ["cdn-app.sberdevices.ru"];
const API_PREFIXES = ["/people/", "/access/", "/shift/", "/assistant/", "/auth/", "/actuator/"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(PRECACHE.map((p) => new Request(p, { cache: "reload" })))).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k.startsWith("proxodnaya-shell-") && k !== SHELL).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

const fromNet = async (req, cacheName) => {
  const res = await fetch(req);
  if (res.ok || res.type === "opaque") (await caches.open(cacheName)).put(req, res.clone());
  return res;
};

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  // Страница: сначала сеть (свежая версия), без сети — оболочка из кэша. Все маршруты SPA отдают один index.html.
  if (req.mode === "navigate") {
    e.respondWith(fromNet(req, SHELL).catch(async () => (await caches.match(req, { ignoreSearch: true })) ?? (await caches.match("/index.html")) ?? (await caches.match("/"))));
    return;
  }
  // Шрифты СберДевайсов: из кэша сразу, обновление в фоне.
  if (FONT_HOSTS.includes(url.host)) {
    e.respondWith(caches.match(req).then((hit) => { const net = fromNet(req, RUNTIME).catch(() => hit); return hit ?? net; }));
    return;
  }
  if (url.origin !== self.location.origin) return;
  // API, SSE и Keycloak — всегда сеть, мимо кэша: данные и решения прохода не должны «залипать».
  if (API_PREFIXES.some((p) => url.pathname.startsWith(p))) return;
  // Чанки с хэшем в имени не меняются — из кэша, иначе из сети с сохранением.
  e.respondWith(caches.match(req, { ignoreSearch: true }).then((hit) => hit ?? fromNet(req, RUNTIME)));
});

self.addEventListener("message", (e) => { if (e.data === "skip-waiting") self.skipWaiting(); });
