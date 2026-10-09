export const routes = {
  home: "/",
  kiosk: "/kiosk",
  worker: "/worker",
  workerActivate: "/worker/activate",
  workerHistory: "/worker/history",
  workerShifts: "/worker/shifts",
  workerProfile: "/worker/profile",
  workerFace: "/worker/face",
  login: "/login",
  admin: "/admin",
  adminPeople: "/admin/people",
  adminPersonNew: "/admin/people/new",
  adminPerson: (id: string) => `/admin/people/${id}`,
  adminJournal: "/admin/journal",
  adminShifts: "/admin/shifts",
  adminAnalytics: "/admin/analytics",
  adminAssistant: "/admin/assistant",
  adminSettings: "/admin/settings",
  adminObjects: "/admin/objects",
  adminTerminals: "/admin/terminals",
  adminAccess: "/admin/access",
} as const;

/** Абсолютная ссылка на маршрут (для QR и «открыть на телефоне»). */
export const absoluteUrl = (path: string) => `${location.origin}${path}`;
