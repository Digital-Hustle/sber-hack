// Синхронизация кэша на терминале и на телефоне сотрудника — у них свои источники, не кабинетные.
import type { Kiosk, Settings, Zone, Site } from "../types";
import { http, apiPath, ApiError } from "./http";
import { getDb, patchDb, resetCache, DEFAULT_SETTINGS } from "./store";
import { toAttempt, toCheckpoint, toKiosk, toShift, toWorker, toZone, toSite, type AttemptRs, type KioskRs, type ShiftRs, type WorkerRs } from "./map";
import { KIOSK_SLOT, PHONE_SLOT, setKioskKey } from "./auth";
import { savedCard } from "./server";
import { createKey, loadKey, publicJwk, saveKey } from "../../lib/deviceKey";

// ——— Терминал ———
type KioskMeRs = { kiosk: KioskRs; checkpoint?: Parameters<typeof toCheckpoint>[0] | null; settings?: Partial<Settings> | null };

/** Первый запуск: ключ терминала (неизвлекаемый) → регистрация → код сопряжения на экране. */
const registerKiosk = async () => {
  const key = await createKey(KIOSK_SLOT, "pending", "kiosk");
  const r = await http<{ kioskId: string; pairCode: string }>("access", "/kiosk/register", { auth: "none", body: { publicKeyJwk: await publicJwk(key.publicKey) } });
  const saved = { ...key, deviceId: r.kioskId };
  await saveKey(KIOSK_SLOT, saved);
  setKioskKey(saved);
  return r.kioskId;
};

/** id терминала (= id его ключа). Нет ключа — регистрируемся. */
export const kioskIdentity = async () => {
  const key = await loadKey(KIOSK_SLOT);
  if (key && key.deviceId !== "pending") return key.deviceId;
  return registerKiosk();
};

/** Состояние терминала с сервера: сопряжён ли, с какой проходной, логика прохода. Заодно — пульс «на связи». */
export const kioskStatus = async () => {
  let r: KioskMeRs;
  try {
    r = await http<KioskMeRs>("access", "/kiosk/me", { auth: "kiosk" });
  } catch (e) {
    // Сервер не знает ключ (база пересоздана, терминал удалён) — регистрируемся заново.
    if (e instanceof ApiError && (e.status === 401 || e.code === "KIOSK_UNKNOWN")) { await registerKiosk(); r = await http<KioskMeRs>("access", "/kiosk/me", { auth: "kiosk" }); }
    else throw e;
  }
  const kiosk: Kiosk = toKiosk(r.kiosk);
  const cp = r.checkpoint ? toCheckpoint(r.checkpoint) : undefined;
  patchDb({ kiosks: [kiosk], checkpoints: cp ? [cp] : [], settings: { ...DEFAULT_SETTINGS, ...getDb().settings, ...(r.settings ?? {}) } });
  return kiosk;
};

// ——— Телефон ———
type WorkerMeRs = { inside: boolean; zoneId?: string | null; since?: string | number | null; workedMsToday: number };
type PeopleMeRs = WorkerRs & { sites?: Site[]; zones?: (Zone & { siteId?: string })[] };

/** Кэш телефона: своя карточка, свои проходы и смены. Без сети — карточка из активации. */
export const loadWorkerData = async () => {
  const key = await loadKey(PHONE_SLOT);
  if (!key) return;
  const card = savedCard();
  const now = Date.now();
  const tasks = await Promise.allSettled([
    http<PeopleMeRs>("people", "/worker/me", { auth: "device" }),
    http<{ items: AttemptRs[] }>("access", "/worker/attempts", { auth: "device", query: { limit: 200 } }),
    http<{ items: ShiftRs[] }>("shift", "/worker/shifts", { auth: "device", query: { from: new Date(now - 14 * 86400000).toISOString(), to: new Date(now + 31 * 86400000).toISOString(), limit: 200 } }),
    http<WorkerMeRs>("shift", "/worker/me", { auth: "device" }),
  ]);
  const [me, attempts, shifts] = tasks;
  const db = getDb();
  const fallback = card ? [{ id: card.workerId, fullName: card.fullName, position: card.position, contractor: card.contractor, status: "active" as const, zoneIds: [], permitUntil: "", createdAt: 0 }] : db.workers;
  patchDb({
    workers: me.status === "fulfilled" ? [toWorker(me.value)] : fallback,
    sites: me.status === "fulfilled" && me.value.sites ? me.value.sites.map(toSite) : (card?.sites ?? db.sites ?? []).map((s) => ({ id: s.id, name: s.name })),
    zones: me.status === "fulfilled" && me.value.zones ? me.value.zones.map(toZone) : db.zones,
    attempts: attempts.status === "fulfilled" ? attempts.value.items.map(toAttempt).sort((a, b) => a.ts - b.ts) : db.attempts,
    shifts: shifts.status === "fulfilled" ? shifts.value.items.map(toShift) : db.shifts,
    devices: [{ id: key.deviceId, workerId: key.workerId, publicKey: key.publicKey, createdAt: key.createdAt, label: "Этот телефон" }],
  });
};

/** Телефон: загрузка, поток своих событий (SSE /stream/worker), опрос раз в минуту. */
export const startWorkerSync = () => {
  resetCache();
  void loadWorkerData();
  let closed = false;
  let es: EventSource | null = null;
  let retry: ReturnType<typeof setTimeout> | undefined;
  const connect = async (attempt = 0) => {
    if (closed) return;
    try {
      const { ticket } = await http<{ ticket: string }>("shift", "/stream/ticket", { auth: "device" });
      if (closed) return;
      es = new EventSource(`${apiPath("shift", "/stream/worker")}?ticket=${encodeURIComponent(ticket)}`);
      es.addEventListener("attempt.result", () => void loadWorkerData());
      es.onerror = () => { es?.close(); retry = setTimeout(() => void connect(attempt + 1), Math.min(60_000, 2000 * 2 ** attempt)); };
    } catch { retry = setTimeout(() => void connect(attempt + 1), Math.min(60_000, 2000 * 2 ** attempt)); }
  };
  void connect();
  const t = setInterval(() => void loadWorkerData(), 60_000);
  return () => { closed = true; es?.close(); clearTimeout(retry); clearInterval(t); };
};
