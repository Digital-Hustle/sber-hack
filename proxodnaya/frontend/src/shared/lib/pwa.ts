// ADR-043: установка как приложение. Три приложения на одном адресе — пропуск, терминал, админка —
// у каждого свой манифест (свой id и стартовый экран). Маршруты на хэше, поэтому манифест меняется по хэшу.
type Prompt = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: "accepted" | "dismissed" }> };

const MANIFEST = { worker: "/manifest.webmanifest", kiosk: "/manifest-kiosk.webmanifest", admin: "/manifest-admin.webmanifest" } as const;
export type PwaApp = keyof typeof MANIFEST;

export const appOf = (hash = location.hash): PwaApp => (hash.startsWith("#/kiosk") ? "kiosk" : hash.startsWith("#/admin") ? "admin" : "worker");

let deferred: Prompt | null = null;
const subs = new Set<() => void>();
const emit = () => subs.forEach((f) => f());

export const installState = {
  subscribe: (f: () => void) => { subs.add(f); return () => { subs.delete(f); }; },
  canInstall: () => !!deferred,
  installed: () => matchMedia("(display-mode: standalone)").matches || matchMedia("(display-mode: fullscreen)").matches,
};

/**
 * Как поставить приложение, если браузер не предлагает установку сам: Safari на iPhone/iPad и Mac
 * не шлёт beforeinstallprompt, Firefox и Samsung Internet на Android — тоже. Тогда показываем инструкцию.
 */
export type InstallHow = "ios" | "ios-other" | "mac-safari" | "android";
export const manualInstall = (ua = navigator.userAgent): InstallHow | null => {
  const ios = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
  if (ios) return /CriOS|FxiOS|EdgiOS|YaBrowser/.test(ua) ? "ios-other" : "ios";
  if (/Macintosh/.test(ua) && /Safari/.test(ua) && !/Chrome|Chromium|Edg\/|Firefox/.test(ua)) return "mac-safari";
  if (/Android/.test(ua) && /Firefox|SamsungBrowser/.test(ua)) return "android";
  return null;
};

export const promptInstall = async () => {
  const p = deferred;
  if (!p) return false;
  deferred = null; emit();
  await p.prompt();
  return (await p.userChoice).outcome === "accepted";
};

const syncManifest = () => {
  const link = document.querySelector<HTMLLinkElement>('link[rel="manifest"]');
  const href = MANIFEST[appOf()];
  if (link && link.getAttribute("href") !== href) link.setAttribute("href", href);
};

/** Регистрирует service worker (только в сборке) и следит за манифестом и предложением установки. */
export const setupPwa = () => {
  syncManifest();
  addEventListener("hashchange", syncManifest);
  addEventListener("beforeinstallprompt", (e) => { e.preventDefault(); deferred = e as Prompt; emit(); });
  addEventListener("appinstalled", () => { deferred = null; emit(); });
  if (import.meta.env.PROD && "serviceWorker" in navigator) {
    const reg = () => { navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(() => undefined); };
    if (document.readyState === "complete") reg(); else addEventListener("load", reg, { once: true });
  }
};
