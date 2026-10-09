// Кэш данных кабинета поверх API: экраны читают его синхронно (useDb), запись — через server.ts,
// после неё нужная часть перечитывается. Живые обновления — SSE обстановки shift + редкий опрос.
// Журнал целиком не грузится: кэш держит проходы за последние ATTEMPTS_DAYS (для обстановки и сводок),
// полный поиск — queryAttempts (постранично на сервере).
import { useSyncExternalStore } from "react";
import type { Db, Role, Settings } from "../types";
import { fetchAll, http, ms, apiPath } from "./http";
import { toAccessEvent, toAdmin, toAttempt, toCheckpoint, toKiosk, toSettings, toShift, toSite, toWorker, toZone } from "./map";
import type { AccessEntryRs, AttemptRs, KioskRs, SettingsRs, ShiftRs, StaffUserRs, WorkerRs } from "./map";
import type { TerminalCodeHash, TerminalCodeKind } from "../../lib/terminalCode";

export const DEFAULT_SETTINGS: Settings = {
  faceThreshold: 0.363, qrToleranceSec: 90, shiftGraceMin: 60, requireShift: true, repeatScanCooldownSec: 30, presenceTtlHours: 16,
  terminalMode: "QR_FACE", terminalScope: "GLOBAL", offlinePolicy: "GUARD", offlineMaxHours: 12, offlineAfterExpiry: "GUARD", offlineCheckShift: true, offlineUnknownDevice: "DENY",
};
const DAY = 86_400_000;
export const ATTEMPTS_DAYS = 8;
const SHIFTS_BACK_DAYS = 14;
const SHIFTS_AHEAD_DAYS = 62;

const empty = (): Db => ({
  version: 1, workers: [], devices: [], zones: [], checkpoints: [], shifts: [], attempts: [], qrUses: [], settings: DEFAULT_SETTINGS,
  kiosks: [], sites: [], admins: [], accessLog: [], terminalCodes: {}, removedCheckpoints: {},
});

let db: Db = empty();
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());
const subscribe = (cb: () => void) => { listeners.add(cb); return () => { listeners.delete(cb); }; };

export const getDb = () => db;
/** Подписка компонента на кэш. Ссылки частей стабильны до следующего обновления этой части. */
export const useDb = () => useSyncExternalStore(subscribe, getDb, getDb);
/** Заменить часть кэша (новая ссылка на db — компоненты перерисуются). */
export const patchDb = (patch: Partial<Db>) => { db = { ...db, ...patch }; emit(); };
export const resetCache = () => { db = empty(); emit(); };

// ——— Загрузка частей ———
export type Part = "objects" | "workers" | "settings" | "shifts" | "attempts" | "kiosks" | "staff" | "codes";

type CodesRs = Partial<Record<TerminalCodeKind, { digits: number; updatedAt: number | string; by: string }>>;

const loaders: Record<Part, () => Promise<void>> = {
  objects: async () => {
    const [sites, zones, cps] = await Promise.all([
      http<{ items: Parameters<typeof toSite>[0][] }>("people", "/sites"),
      http<{ items: Parameters<typeof toZone>[0][] }>("people", "/zones"),
      http<{ items: (Parameters<typeof toCheckpoint>[0] & { deleted?: boolean })[] }>("people", "/checkpoints", { query: { includeDeleted: true } }),
    ]);
    const removed: Db["removedCheckpoints"] = {};
    for (const c of cps.items) if (c.deleted) removed[c.id] = { name: c.name, zoneId: c.zoneId };
    patchDb({ sites: sites.items.map(toSite), zones: zones.items.map(toZone), checkpoints: cps.items.filter((c) => !c.deleted).map(toCheckpoint), removedCheckpoints: removed });
  },
  workers: async () => { patchDb({ workers: (await fetchAll<WorkerRs>("people", "/workers")).map(toWorker) }); },
  settings: async () => { patchDb({ settings: toSettings(await http<SettingsRs>("access", "/settings")) }); },
  shifts: async () => {
    const now = Date.now();
    const items = await fetchAll<ShiftRs>("shift", "/shifts", { from: new Date(now - SHIFTS_BACK_DAYS * DAY).toISOString(), to: new Date(now + SHIFTS_AHEAD_DAYS * DAY).toISOString() });
    patchDb({ shifts: items.map(toShift) });
  },
  attempts: async () => {
    const from = new Date(Date.now() - ATTEMPTS_DAYS * DAY).toISOString();
    const items = (await fetchAll<AttemptRs>("access", "/attempts", { from })).filter((a) => a.source !== "SIMULATOR");
    patchDb({ attempts: items.map(toAttempt).sort((a, b) => a.ts - b.ts) });
  },
  kiosks: async () => { patchDb({ kiosks: (await http<{ items: KioskRs[] }>("access", "/kiosks")).items.map(toKiosk) }); },
  staff: async () => {
    const [users, log] = await Promise.all([fetchAll<StaffUserRs>("people", "/staff"), http<{ items: AccessEntryRs[] }>("people", "/staff/access-log", { query: { limit: 200 } })]);
    patchDb({ admins: users.map(toAdmin), accessLog: log.items.map(toAccessEvent) });
  },
  codes: async () => {
    const r = await http<CodesRs>("access", "/terminal-codes");
    const codes: Partial<Record<TerminalCodeKind, TerminalCodeHash>> = {};
    for (const k of ["service", "guard"] as const) {
      const v = r[k];
      if (v) codes[k] = { salt: "", hash: "", iter: 0, digits: v.digits, updatedAt: ms(v.updatedAt) ?? 0, by: v.by };
    }
    patchDb({ terminalCodes: codes });
  },
};

/** Что читает роль (матрица — entities/session, права проверяет сервер). */
const PARTS: Record<Role, Part[]> = {
  ADMIN: ["objects", "workers", "settings", "shifts", "attempts", "kiosks", "staff", "codes"],
  SECURITY_OFFICER: ["objects", "workers", "settings", "shifts", "attempts", "kiosks", "codes"],
  MANAGER: ["objects", "workers", "settings", "shifts", "attempts"],
  INSTALLER: ["objects", "kiosks", "codes"],
  GUARD: ["objects", "workers", "attempts"],
};

const errors = new Map<Part, string>();
export const loadError = (p: Part) => errors.get(p);

/** Перечитать части кэша. Ошибка одной части не мешает остальным (экран покажет пустое состояние). */
export const reload = async (...parts: Part[]) => {
  await Promise.all(parts.map(async (p) => {
    try { await loaders[p](); errors.delete(p); } catch (e) { errors.set(p, e instanceof Error ? e.message : String(e)); emit(); }
  }));
};

// ——— Живые обновления ———
const POLL_MS = 30_000;
const SLOW_POLL_MS = 120_000;
let stopSync: (() => void) | null = null;

/** Запустить синхронизацию кэша для роли: первая загрузка, SSE обстановки, опрос. Повторный вызов перезапускает. */
export const startSync = (role: Role) => {
  stopSync?.();
  resetCache();
  const parts = PARTS[role];
  void reload(...parts);
  let closed = false;
  let es: EventSource | null = null;
  let retry: ReturnType<typeof setTimeout> | undefined;
  let debounce: ReturnType<typeof setTimeout> | undefined;
  const live = parts.filter((p) => p === "attempts");
  const onEvent = () => { clearTimeout(debounce); debounce = setTimeout(() => void reload(...live), 400); };
  const connect = async (attempt = 0) => {
    if (closed || !live.length) return;
    try {
      const { ticket } = await http<{ ticket: string }>("shift", "/stream/ticket");
      if (closed) return;
      es = new EventSource(`${apiPath("shift", "/stream/situation")}?ticket=${encodeURIComponent(ticket)}`);
      es.addEventListener("attempt.created", onEvent);
      es.addEventListener("presence.changed", onEvent);
      es.onopen = () => { if (attempt > 0) onEvent(); attempt = 0; };
      es.onerror = () => { es?.close(); retry = setTimeout(() => void connect(attempt + 1), Math.min(30_000, 1000 * 2 ** attempt)); };
    } catch { retry = setTimeout(() => void connect(attempt + 1), Math.min(30_000, 1000 * 2 ** attempt)); }
  };
  void connect();
  const fast = setInterval(() => void reload(...parts.filter((p) => p === "attempts" || p === "kiosks")), POLL_MS);
  const slow = setInterval(() => void reload(...parts.filter((p) => p !== "attempts" && p !== "kiosks")), SLOW_POLL_MS);
  stopSync = () => { closed = true; es?.close(); clearTimeout(retry); clearTimeout(debounce); clearInterval(fast); clearInterval(slow); stopSync = null; };
  return stopSync;
};
export const endSync = () => { stopSync?.(); resetCache(); };
