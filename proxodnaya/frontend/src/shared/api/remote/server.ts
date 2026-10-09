// Вызовы сервисов (people, access, shift) с той же поверхностью, что ждут экраны. Решение о проходе принимает сервер:
// здесь только запрос и разбор ответа. После записи перечитывается нужная часть кэша (store.ts).
import type { AccessEvent, AdminUser, Attempt, Challenge, Checkpoint, DecisionResult, Device, FaceCheck, Kiosk, ManualReview, OfflineBatch,
  OfflinePolicy, OfflineSnapshot, OfflineSyncResult, Role, Settings, Shift, Site, TerminalMode, Worker, Zone, Db } from "../types";
import { download, fetchAll, http, ms } from "./http";
import { getDb, patchDb, reload } from "./store";
import { presenceNow, shiftFor } from "./derived";
import { toAttempt, toCheckpoint, toSite, toWorker, toZone, type AttemptRs, type WorkerRs } from "./map";
import { applyStaffSession, clearStaffSession, getStaffUser, clearDeviceToken, PHONE_SLOT, type StaffUser } from "./auth";
import { createKey, deleteKey, publicJwk, saveKey } from "../../lib/deviceKey";
import { randomId } from "../../lib/id";
import { atTime, todayKey } from "../../lib/time";
import { motion as m } from "../../config/tokens";
import type { TerminalCodeKind } from "../../lib/terminalCode";

// ——— Проход (киоск) ———
type PublicWorker = Pick<Worker, "id" | "fullName" | "position" | "photo" | "contractor">;
type WorkerBrief = { id: string; fullName: string; position?: string; contractor?: string; photoThumb?: string | null };
const brief = (w?: WorkerBrief | null): PublicWorker | undefined =>
  w ? { id: w.id, fullName: w.fullName, position: w.position ?? "", contractor: w.contractor ?? "", photo: w.photoThumb ?? undefined } : undefined;

type DecisionRs = Omit<DecisionResult, "worker" | "ts"> & { worker?: WorkerBrief | null; ts: string | number };
const toDecision = (r: DecisionRs): DecisionResult => ({ ...r, worker: brief(r.worker), ts: ms(r.ts) ?? Date.now() });

export type ScanResult =
  | { kind: "decision"; result: DecisionResult }
  | { kind: "challenge"; token: string; challenge: Challenge; worker: PublicWorker };
type ScanRs = { kind: "decision"; result: DecisionRs } | { kind: "challenge"; token: string; challenge: Challenge; worker: WorkerBrief };

/** QR → решение или челлендж живости. Направление выводит сервер (ADR-037). */
export const kioskScan = async (raw: string, checkpointId: string): Promise<ScanResult> => {
  const r = await http<ScanRs>("access", "/kiosk/attempts", { auth: "kiosk", body: { qr: raw, checkpointId } });
  return r.kind === "decision" ? { kind: "decision", result: toDecision(r.result) } : { kind: "challenge", token: r.token, challenge: r.challenge, worker: brief(r.worker)! };
};

/** Кадры челленджа (JPEG data-URL) → решение. */
export const kioskFrames = async (token: string, frames: string[]) =>
  toDecision(await http<DecisionRs>("access", `/kiosk/attempts/${encodeURIComponent(token)}/frames`, { auth: "kiosk", body: { frames } }));

/** ADR-038: для поиска по всей базе (1:N) порог строже, чем для сверки 1:1 по QR. */
export const FACE_FIRST_MARGIN = 0.1;
/** «Сначала лицо»: кадры → идентификация 1:N на сервере → те же правила. Не узнали — FACE_NOT_FOUND, киоск предлагает QR. */
export const kioskIdentify = async (checkpointId: string, frames: string[]) =>
  toDecision(await http<DecisionRs>("access", "/kiosk/identify", { auth: "kiosk", body: { checkpointId, frames } }));

const CHALLENGES: Omit<Challenge, "timeoutMs">[] = [
  { kind: "turn-left", text: "Медленно поверните голову влево" },
  { kind: "turn-right", text: "Медленно поверните голову вправо" },
  { kind: "blink", text: "Моргните два раза" },
  { kind: "nod", text: "Кивните" },
];
/** Подсказка живости для «Сначала лицо» (кадры всё равно проверяет сервер). */
export const faceChallenge = (): Challenge => ({ ...CHALLENGES[Math.floor(Math.random() * CHALLENGES.length)], timeoutMs: m.kiosk.challengeTimeoutMs });

/** Ручной пропуск охранником (ADR-040): причина обязательна, запись ждёт подтверждения вторым человеком. */
export const kioskManual = async (workerId: string, checkpointId: string, note: string) =>
  toDecision(await http<DecisionRs>("access", "/kiosk/manual", { auth: "kiosk", body: { workerId, checkpointId, note } }));

/** Подтвердить или оспорить ручной пропуск. */
export const reviewManual = async (id: string, status: ManualReview["status"], _by: string, comment?: string) => {
  await http("access", `/attempts/${encodeURIComponent(id)}/review`, { body: { status, comment: comment?.trim() || undefined } });
  await reload("attempts");
};

// ——— Терминалы (ADR-038) ———
export const KIOSK_ONLINE_MS = 45000;
const perKiosk = (db: Db) => db.settings.terminalScope === "PER_KIOSK";
export const terminalModeOf = (db: Db, kiosk?: Kiosk): TerminalMode => (perKiosk(db) ? kiosk?.mode : undefined) ?? db.settings.terminalMode ?? "QR_FACE";
export const offlinePolicyOf = (db: Db, kiosk?: Kiosk): OfflinePolicy => (perKiosk(db) ? kiosk?.offlinePolicy : undefined) ?? db.settings.offlinePolicy ?? "GUARD";

export const pairKiosk = async (code: string, p: { name: string; checkpointId: string; mode?: TerminalMode; offlinePolicy?: OfflinePolicy }) => {
  const r = await http<{ id: string }>("access", "/kiosks/pair", { body: { pairCode: code.trim().toUpperCase(), name: p.name.trim() || "Терминал", checkpointId: p.checkpointId, mode: p.mode, offlinePolicy: p.offlinePolicy } });
  await reload("kiosks");
  return r.id;
};
export const updateKiosk = async (id: string, patch: Partial<Pick<Kiosk, "name" | "checkpointId" | "mode" | "offlinePolicy">>) => {
  await http("access", `/kiosks/${id}`, { method: "PATCH", body: patch });
  await reload("kiosks");
};
/** Отвязать: терминал сразу перестаёт пропускать и показывает новый код сопряжения. */
export const unpairKiosk = async (id: string) => {
  await http("access", `/kiosks/${id}/unpair`, { method: "POST" });
  await reload("kiosks");
};

type SnapshotRs = Omit<OfflineSnapshot, "at" | "checkpoint"> & { at: string | number; checkpoint: Parameters<typeof toCheckpoint>[0];
  shifts: { id: string; workerId: string; start: string | number; end: string | number }[] };
/** ADR-042: снимок допусков для автономной проверки (только открытые данные). */
export const kioskSnapshot = async (): Promise<OfflineSnapshot | null> => {
  const s = await http<SnapshotRs | undefined>("access", "/kiosk/offline-snapshot", { auth: "kiosk" });
  if (!s) return null;
  const shifts: Shift[] = s.shifts.map((x) => {
    const a = ms(x.start) ?? 0, b = ms(x.end) ?? 0;
    const d = new Date(a), e = new Date(b);
    const hm = (t: Date) => `${String(t.getHours()).padStart(2, "0")}:${String(t.getMinutes()).padStart(2, "0")}`;
    return { id: x.id, workerId: x.workerId, day: todayKey(d), start: hm(d), end: hm(e) };
  });
  return { ...s, at: ms(s.at) ?? Date.now(), checkpoint: toCheckpoint(s.checkpoint), shifts };
};

/** ADR-043: пакет проходов без связи, подписанный ключом терминала. Принятые id сервер возвращает в accepted. */
export const syncOffline = (batch: OfflineBatch, signature: string) =>
  http<OfflineSyncResult>("access", "/kiosk/offline-attempts", { auth: "kiosk", body: { batch, signature } });

// ——— Телефон сотрудника ———
export type WorkerCard = { workerId: string; fullName: string; position: string; contractor: string; photoThumb?: string; sites: { id: string; name: string }[] };
const CARD_KEY = "proxodnaya.worker.card";
/** Карточка на телефоне — для экрана без сети (не секрет: ФИО и объекты, без токенов). */
export const savedCard = (): WorkerCard | null => { try { return JSON.parse(localStorage.getItem(CARD_KEY) ?? "null") as WorkerCard | null; } catch { return null; } };
export const forgetCard = () => localStorage.removeItem(CARD_KEY);

/** Активация: ключ создаётся здесь (неизвлекаемый), на сервер уходит только публичный JWK. */
export const activateDevice = async (code: string): Promise<Worker> => {
  const deviceId = randomId("d", 12);
  const key = await createKey(PHONE_SLOT, deviceId, "");
  try {
    const r = await http<{ workerId: string; card: Omit<WorkerCard, "workerId" | "sites"> & { sites?: WorkerCard["sites"] } }>("people", "/auth/worker/activate", {
      auth: "none", body: { inviteCode: code.trim().toUpperCase(), deviceId, publicKeyJwk: await publicJwk(key.publicKey) },
    });
    await saveKey(PHONE_SLOT, { ...key, workerId: r.workerId });
    clearDeviceToken();
    const card: WorkerCard = { workerId: r.workerId, fullName: r.card.fullName, position: r.card.position ?? "", contractor: r.card.contractor ?? "", photoThumb: r.card.photoThumb ?? undefined, sites: r.card.sites ?? [] };
    localStorage.setItem(CARD_KEY, JSON.stringify(card));
    return { id: r.workerId, fullName: card.fullName, position: card.position, contractor: card.contractor, status: "active", zoneIds: [], permitUntil: "", createdAt: Date.now() };
  } catch (e) {
    await deleteKey(PHONE_SLOT).catch(() => undefined);
    throw e;
  }
};

/** Выйти с телефона: сервер отзывает ключ (DELETE /worker/me/device), закрытая часть удаляется. */
export const unbindMyDevice = async () => {
  await http("people", "/worker/me/device", { method: "DELETE", auth: "device" }).catch(() => undefined);
  clearDeviceToken();
};

/** Отвязать телефон сотрудника из кабинета. */
export const revokeDevice = async (deviceId: string) => {
  const dev = getDb().devices.find((d) => d.id === deviceId);
  if (!dev) throw new Error("Телефон не найден");
  await http("people", `/workers/${dev.workerId}/devices/${encodeURIComponent(deviceId)}`, { method: "DELETE" });
  await loadDevices(dev.workerId);
};

type DeviceRs = { deviceId: string; status: string; boundAt?: string | number; revokedAt?: string | number | null; label?: string; publicKey?: string };
/** Телефоны сотрудника — подгружаются в кэш при открытии карточки. */
export const loadDevices = async (workerId: string) => {
  const r = await http<{ items: DeviceRs[] }>("people", `/workers/${workerId}/devices`);
  const mine: Device[] = r.items.map((d) => ({ id: d.deviceId, workerId, publicKey: d.publicKey ?? "", createdAt: ms(d.boundAt) ?? 0, revokedAt: ms(d.revokedAt ?? undefined), label: d.label ?? "Телефон" }));
  patchDb({ devices: [...getDb().devices.filter((d) => d.workerId !== workerId), ...mine] });
};

// ——— Люди ———
export type WorkerDraft = Pick<Worker, "fullName" | "position" | "contractor" | "zoneIds"> & { photo?: string; permitUntil?: string };
const DEFAULT_PERMIT_DAYS = 180;
const permitsFor = (until: string) => [{ type: "BRIEFING", validUntil: until }, { type: "MEDICAL", validUntil: until }];

/** Завести сотрудника: карточка → эталон лица (если снят) → код приглашения. */
export const createWorker = async (draft: WorkerDraft, _byId?: string) => {
  const until = draft.permitUntil ?? todayKey(new Date(Date.now() + DEFAULT_PERMIT_DAYS * 86400000));
  const w = toWorker(await http<WorkerRs>("people", "/workers", { body: {
    fullName: cleanName(draft.fullName), position: draft.position.trim(), contractor: draft.contractor.trim(), zoneIds: draft.zoneIds, permits: permitsFor(until),
  } }));
  if (draft.photo) await http("people", `/workers/${w.id}/face`, { body: { frames: [draft.photo] } });
  const inv = await http<{ inviteCode: string }>("people", `/workers/${w.id}/invite`, { method: "POST" });
  await reload("workers");
  return { ...w, inviteCode: inv.inviteCode, face: draft.photo ? { status: "ACTIVE" as const, source: "HR" as const, at: Date.now() } : w.face };
};

const invites = new Map<string, { code: string; link: string; expiresAt: number }>();
/** Последний выданный код приглашения (сервер хранит только HMAC — показать можно только что выданный). */
export const inviteOf = (workerId: string) => invites.get(workerId);

/** Блокировка, допуск по сроку и фото — частные случаи правки карточки. */
export const updateWorker = async (id: string, patch: Partial<Worker>) => {
  if (patch.status) await http("people", `/workers/${id}/${patch.status === "blocked" ? "block" : "unblock"}`, { method: "POST" });
  if (patch.permitUntil) await http("people", `/workers/${id}`, { method: "PATCH", body: { permits: permitsFor(patch.permitUntil) } });
  if (patch.photo) await http("people", `/workers/${id}/face`, { body: { frames: [patch.photo] } });
  await reload("workers");
};

export const regenerateInvite = async (id: string) => {
  const r = await http<{ inviteCode: string; link: string; expiresAt: string | number }>("people", `/workers/${id}/invite`, { method: "POST" });
  invites.set(id, { code: r.inviteCode, link: r.link, expiresAt: ms(r.expiresAt) ?? 0 });
  const db = getDb();
  patchDb({ workers: db.workers.map((w) => (w.id === id ? { ...w, inviteCode: r.inviteCode } : w)) });
  return r.inviteCode;
};

export type WorkerEdit = Partial<Pick<Worker, "fullName" | "position" | "contractor" | "zoneIds">>;
export const editWorker = async (id: string, patch: WorkerEdit, _byId: string) => {
  if (patch.zoneIds && !patch.zoneIds.length) throw new Error("Оставьте хотя бы одну зону допуска");
  await http("people", `/workers/${id}`, { method: "PATCH", body: { ...patch, fullName: patch.fullName !== undefined ? cleanName(patch.fullName) : undefined } });
  await reload("workers");
};
export const deleteWorker = async (id: string, _byId: string) => {
  await http("people", `/workers/${id}`, { method: "DELETE" });
  await reload("workers", "shifts");
};

// ——— Эталон лица (ADR-046/047) ———
export const faceMessage = (workerId: string, deviceId: string, at: number) => `face|${workerId}|${deviceId}|${at}`;
/** Селфи-эталон с телефона, подписанное ключом телефона. Включается только после подтверждения человеком. */
export const enrollFace = async (p: { at: number; signature: string; photo: string; frames: string[] }) => {
  await http("people", "/worker/me/face", { auth: "device", body: { at: p.at, signature: p.signature, photo: p.photo, liveness: { frames: p.frames } } });
};
export const reviewFace = async (workerId: string, approve: boolean, _byId: string, comment?: string) => {
  await http("people", `/workers/${workerId}/face/review`, { body: { approve, comment: comment?.trim() || undefined } });
  await reload("workers");
};

const FACE_TEXT: Record<FaceCheck["code"], string> = {
  OK: "Он станет эталоном — терминалы будут сверять лицо с ним",
  NO_FACE: "Человек должен смотреть в камеру, лицо — в овале рамки",
  TOO_DARK: "Повернитесь к свету или включите освещение",
  TOO_BRIGHT: "Уберите яркий свет за спиной или сбоку",
  BLURRY: "Попросите не двигаться и снимите ещё раз",
  TOO_SMALL: "Подойдите ближе к камере",
  DUPLICATE: "Один человек — одна карточка. Откройте найденную карточку или переснимите, если это другой человек",
};
type FaceCheckRs = { ok: boolean; code: string; message: string; hint?: string; score?: number; quality?: number; duplicate?: { workerId: string; fullName: string } | null };
/** Проверка снимка до сохранения карточки: качество (YuNet) и повтор лица (SFace 1:N) — на сервере face. */
export const checkFacePhoto = async (photo: string, exceptWorkerId?: string): Promise<FaceCheck> => {
  const r = await http<FaceCheckRs>("people", "/workers/face/check", { body: { photo, exceptWorkerId } });
  const code = (r.code in FACE_TEXT ? r.code : r.ok ? "OK" : "NO_FACE") as FaceCheck["code"];
  return { ok: r.ok, code, message: r.message, hint: r.hint ?? FACE_TEXT[code], score: r.score ?? r.quality ?? 0, duplicate: r.duplicate ?? undefined };
};
/** Переснять эталон из карточки: та же проверка, старый эталон заменяется. */
export const setWorkerFace = async (workerId: string, photo: string, _byId: string) => {
  await http("people", `/workers/${workerId}/face`, { body: { frames: [photo] } });
  await reload("workers");
};

// ——— Смены ———
const shiftBody = (s: { workerId: string; day: string; start: string; end: string }) => {
  const a = atTime(s.day, s.start);
  let b = atTime(s.day, s.end);
  if (b <= a) b += 86400000;
  return { workerId: s.workerId, start: new Date(a).toISOString(), end: new Date(b).toISOString() };
};
/** Смена на день: прежняя смена человека в этот день заменяется. */
export const upsertShift = async (s: Omit<Shift, "id"> & { id?: string }) => {
  for (const x of getDb().shifts.filter((v) => v.workerId === s.workerId && v.day === s.day)) await http("shift", `/shifts/${x.id}`, { method: "DELETE" });
  await http("shift", "/shifts", { body: shiftBody(s) });
  await reload("shifts");
};
export type WeekDayPlan = { weekday: number; start: string; end: string };
/** График по дням недели (своё время на каждый день, ADR-046); смены в эти дни заменяются. */
export const assignSchedule = async (p: { workerIds: string[]; weekdays: number[]; from: string; to: string; start: string; end: string; days?: WeekDayPlan[] }) => {
  const days = p.days ?? p.weekdays.map((weekday) => ({ weekday, start: p.start, end: p.end }));
  for (const t of days) if (!(t.start < t.end)) throw new Error("Конец смены должен быть позже начала");
  const wd = new Set(days.map((d) => d.weekday));
  const old = getDb().shifts.filter((x) => p.workerIds.includes(x.workerId) && x.day >= p.from && x.day <= p.to && wd.has((new Date(`${x.day}T12:00:00`).getDay() + 6) % 7));
  for (const x of old) await http("shift", `/shifts/${x.id}`, { method: "DELETE" });
  const r = await http<{ created: number }>("shift", "/shifts/bulk", { body: { workerIds: p.workerIds, from: p.from, to: p.to, days } });
  await reload("shifts");
  return r.created;
};
export const deleteShift = async (id: string) => { await http("shift", `/shifts/${id}`, { method: "DELETE" }); await reload("shifts"); };

// ——— Настройки ———
export const updateSettings = async (patch: Partial<Settings>) => {
  const cur = { ...getDb().settings, ...patch };
  await http("access", "/settings", { method: "PUT", body: cur });
  await reload("settings");
};

// ——— Постраничные запросы (ADR-039) ———
export type Page<T> = { items: T[]; nextCursor: string | null; total: number };
export const PAGE_MAX = 100;
const clampLimit = (n?: number) => Math.min(Math.max(1, n ?? 40), PAGE_MAX);
const pageByOffset = <T,>(all: T[], cursor: string | null | undefined, limit?: number): Page<T> => {
  const off = cursor ? Math.max(0, parseInt(cursor, 36) || 0) : 0;
  const n = clampLimit(limit);
  return { items: all.slice(off, off + n), nextCursor: off + n < all.length ? (off + n).toString(36) : null, total: all.length };
};
const norm = (s?: string) => (s ?? "").trim().toLowerCase().replace(/ё/g, "е");

export type WorkerFilter = "all" | "inside" | "blocked";
export type WorkerQuery = { q?: string; filter?: WorkerFilter; zoneId?: string; contractor?: string; cursor?: string | null; limit?: number };
export type WorkerRow = Worker & { inside: boolean; insideZoneId?: string };
/** Люди: справочник уже в кэше (обновляется опросом), фильтр и страницы — по нему. */
export const queryWorkers = async (p: WorkerQuery): Promise<Page<WorkerRow>> => {
  const db = getDb();
  const pres = new Map(presenceNow(db).map((x) => [x.workerId, x.zoneId]));
  const q = norm(p.q);
  const all = db.workers
    .filter((w) => (p.filter === "inside" ? pres.has(w.id) : p.filter === "blocked" ? w.status === "blocked" : true))
    .filter((w) => !p.zoneId || w.zoneIds.includes(p.zoneId))
    .filter((w) => !p.contractor || w.contractor === p.contractor)
    .filter((w) => !q || norm(`${w.fullName} ${w.position} ${w.contractor}`).includes(q))
    .sort((a, b) => a.fullName.localeCompare(b.fullName, "ru") || (a.id < b.id ? -1 : 1))
    .map((w) => ({ ...w, inside: pres.has(w.id), insideZoneId: pres.get(w.id) }));
  return pageByOffset(all, p.cursor, p.limit);
};

export type AttemptQuery = { review?: "PENDING"; day?: string; decision?: string; direction?: string; checkpointId?: string; workerId?: string; q?: string; cursor?: string | null; limit?: number;
  /** Свои проходы с телефона (device-токен). */
  mine?: boolean };
const attemptParams = (p: AttemptQuery) => {
  const day = p.day && p.day !== "all" ? p.day : undefined;
  return {
    from: day ? new Date(atTime(day, "00:00")).toISOString() : undefined,
    to: day ? new Date(atTime(day, "00:00") + 86400000).toISOString() : undefined,
    decision: p.decision, direction: p.direction, checkpoint: p.checkpointId, worker: p.mine ? undefined : p.workerId, review: p.review, q: p.q?.trim() || undefined,
  };
};
/** Журнал проходов: страницы, фильтры и поиск — на сервере (keyset-курсор). */
export const queryAttempts = async (p: AttemptQuery): Promise<Page<Attempt>> => {
  const r = await http<{ items: AttemptRs[]; page: { next: string | null; total?: number } }>("access", p.mine ? "/worker/attempts" : "/attempts", {
    auth: p.mine ? "device" : "staff", query: { ...attemptParams(p), cursor: p.cursor, limit: clampLimit(p.limit) },
  });
  return { items: r.items.map(toAttempt), nextCursor: r.page.next, total: r.page.total ?? r.items.length };
};
/** Выгрузка CSV — по тем же фильтрам (до 5000 строк). */
export const exportAttempts = async (p: AttemptQuery): Promise<Attempt[]> => (await fetchAll<AttemptRs>("access", "/attempts", attemptParams(p))).map(toAttempt);
export const downloadTimesheet = (from: string, to: string) => download("shift", "/reports/timesheet.xlsx", `timesheet-${from}_${to}.xlsx`, { from, to });

export type ShiftFilter = "all" | "planned" | "unplanned";
export type ShiftRow = { worker: Worker; shift?: Shift; intervals: { start: number; end?: number }[] };
type IntervalRs = { workerId: string; enteredAt: string | number; exitedAt?: string | number | null };
/** План и факт на день: смены — из кэша, интервалы присутствия — из shift для людей на странице. */
export const queryShiftRows = async (p: { day: string; q?: string; filter?: ShiftFilter; cursor?: string | null; limit?: number }): Promise<Page<ShiftRow>> => {
  const db = getDb();
  const q = norm(p.q);
  const shifts = new Map(db.shifts.filter((s) => s.day === p.day).map((s) => [s.workerId, s]));
  const all = db.workers
    .filter((w) => !q || norm(`${w.fullName} ${w.position} ${w.contractor}`).includes(q))
    .filter((w) => (p.filter === "planned" ? shifts.has(w.id) : p.filter === "unplanned" ? !shifts.has(w.id) : true))
    .sort((a, b) => a.fullName.localeCompare(b.fullName, "ru") || (a.id < b.id ? -1 : 1));
  const page = pageByOffset(all, p.cursor, p.limit);
  const from = atTime(p.day, "00:00");
  let iv: IntervalRs[] = [];
  try { iv = await fetchAll<IntervalRs>("shift", "/intervals", { from: new Date(from).toISOString(), to: new Date(from + 86400000).toISOString() }, 5000); } catch { /* нет прав или shift недоступен — план без факта */ }
  const ids = new Set(page.items.map((w) => w.id));
  const mine = iv.filter((i) => ids.has(i.workerId)).map((i) => ({ workerId: i.workerId, start: ms(i.enteredAt) ?? 0, end: ms(i.exitedAt ?? undefined) }));
  return { ...page, items: page.items.map((w) => ({ worker: w, shift: shifts.get(w.id), intervals: mine.filter((i) => i.workerId === w.id).map(({ start, end }) => ({ start, end })) })) };
};

// ——— Доступ к кабинету (ADR-041) ———
const refreshStaff = () => reload("staff");
export const inviteAdmin = async (p: { name: string; email: string; role: Role }, _byId: string): Promise<AdminUser> => {
  const u = await http<AdminUser>("people", "/staff", { body: { name: p.name.trim().replace(/\s+/g, " "), email: p.email.trim().toLowerCase(), role: p.role } });
  await refreshStaff();
  return u;
};
export const setAdminRole = async (id: string, role: Role, _byId: string) => { await http("people", `/staff/${id}`, { method: "PATCH", body: { role } }); await refreshStaff(); };
export const setAdminActive = async (id: string, active: boolean, _byId: string) => { await http("people", `/staff/${id}/${active ? "enable" : "disable"}`, { method: "POST" }); await refreshStaff(); };

// ——— Коды на терминалах (ADR-046) ———
export const CODE_ROLES: Record<TerminalCodeKind, Role[]> = { service: ["ADMIN", "SECURITY_OFFICER", "INSTALLER"], guard: ["ADMIN", "SECURITY_OFFICER"] };
/** Сменить код: роль и стойкость проверяет сервер (422 CODE_WEAK), хранится только PBKDF2. */
export const setTerminalCode = async (kind: TerminalCodeKind, code: string, _byId: string) => {
  await http("access", `/terminal-codes/${kind}`, { method: "PUT", body: { code } });
  await reload("codes");
};

// ——— Вход в кабинет по коду из письма (ADR-041) ———
export const LOGIN_CODE_LEN = 6;
export const LOGIN_TTL_MS = 10 * 60_000;
export const LOGIN_RESEND_MS = 45_000;
export const maskEmail = (e: string) => { const [u, d] = e.split("@"); return `${u.slice(0, Math.min(3, Math.max(1, u.length - 2)))}${"•".repeat(Math.max(2, u.length - 3))}@${d}`; };
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Отправить код. Ответ одинаковый для любого адреса. */
export const requestLoginCode = async (rawEmail: string) => {
  const email = rawEmail.trim().toLowerCase();
  if (!EMAIL.test(email)) throw new Error("Проверьте адрес почты");
  return http<{ sentTo: string; expiresAt: number; resendAt: number }>("people", "/auth/staff/code", { auth: "none", body: { email } });
};
/** Проверить код → сессия (access в памяти, refresh — httpOnly-cookie). */
export const verifyLoginCode = async (rawEmail: string, code: string) => {
  const r = await http<{ accessToken: string; refreshToken?: string; expiresAt: number; sessionExpiresAt: number; user: StaffUser }>("people", "/auth/staff/code/verify", {
    auth: "none", body: { email: rawEmail.trim().toLowerCase(), code },
  });
  const user = applyStaffSession(r);
  return { user, token: r.accessToken, expiresAt: r.sessionExpiresAt };
};
/** Текущий пользователь кабинета (из сессии). Параметры — для совместимости экранов. */
export const sessionUser = (_db?: Db, _token?: string | null) => getStaffUser();
export const signOutAdmin = async (_token?: string | null) => {
  await http("people", "/auth/staff/logout", { method: "POST" }).catch(() => undefined);
  clearStaffSession();
};

// ——— Объекты, зоны, проходные (ADR-047) ———
const cleanName = (s: string) => s.trim().replace(/\s+/g, " ");
const objects = () => reload("objects");
export const createSite = async (p: { name: string; address?: string }, _byId: string): Promise<Site> => {
  const s = toSite(await http("people", "/sites", { body: { name: cleanName(p.name), address: p.address?.trim() || undefined } }));
  await objects();
  return s;
};
export const updateSite = async (id: string, p: { name?: string; address?: string }, _byId: string) => {
  await http("people", `/sites/${id}`, { method: "PATCH", body: { name: p.name !== undefined ? cleanName(p.name) : undefined, address: p.address } });
  await objects();
};
export const deleteSite = async (id: string, _byId: string) => { await http("people", `/sites/${id}`, { method: "DELETE" }); await objects(); };
export const createZone = async (p: { siteId: string; name: string; capacity: number }, _byId: string): Promise<Zone> => {
  const z = toZone(await http("people", "/zones", { body: { siteId: p.siteId, name: cleanName(p.name), capacity: p.capacity } }));
  await objects();
  return z;
};
export const updateZone = async (id: string, p: { name?: string; capacity?: number; siteId?: string }, _byId: string) => {
  await http("people", `/zones/${id}`, { method: "PATCH", body: { ...p, name: p.name !== undefined ? cleanName(p.name) : undefined } });
  await objects();
};
/** Что потеряется при удалении зоны — по кэшу (сервер всё равно проверит и ответит 409). */
export const zoneUsage = (db: Db, id: string) => ({
  checkpoints: db.checkpoints.filter((c) => c.zoneId === id).length,
  permits: db.workers.filter((w) => w.zoneIds.includes(id)).length,
  inside: presenceNow(db).filter((p) => p.zoneId === id).length,
});
export const deleteZone = async (id: string, _byId: string) => {
  const u = zoneUsage(getDb(), id);
  await http("people", `/zones/${id}`, { method: "DELETE" });
  await reload("objects", "workers");
  return u;
};
export const createCheckpoint = async (p: { zoneId: string; name: string; mode?: Checkpoint["mode"] }, _byId: string): Promise<Checkpoint> => {
  const c = toCheckpoint(await http("people", "/checkpoints", { body: { zoneId: p.zoneId, name: cleanName(p.name), mode: p.mode } }));
  await objects();
  return c;
};
export const editCheckpoint = async (id: string, p: { name?: string; zoneId?: string; mode?: Checkpoint["mode"] }, _byId: string) => {
  await http("people", `/checkpoints/${id}`, { method: "PATCH", body: { ...p, name: p.name !== undefined ? cleanName(p.name) : undefined } });
  await objects();
};
/** ADR-037: режим КПП — авто или фиксированное направление. */
export const updateCheckpoint = async (id: string, patch: Partial<Pick<Checkpoint, "mode">>) => {
  await http("people", `/checkpoints/${id}`, { method: "PATCH", body: patch });
  await objects();
};
export const checkpointKiosks = (db: Db, id: string) => (db.kiosks ?? []).filter((k) => k.pairedAt && k.checkpointId === id);
export const deleteCheckpoint = async (id: string, _byId: string) => {
  const n = checkpointKiosks(getDb(), id).length;
  await http("people", `/checkpoints/${id}`, { method: "DELETE" });
  await reload("objects", "kiosks");
  return n;
};
export const checkpointName = (db: Db, id: string) => db.checkpoints.find((c) => c.id === id)?.name ?? (db.removedCheckpoints?.[id] ? `${db.removedCheckpoints[id].name} (удалена)` : id);

/** Направление для подсказки охраннику (решение всё равно принимает сервер). */
export const resolveDirection = (db: Db, workerId: string, checkpointId: string, now = Date.now()) => {
  const mode = db.checkpoints.find((c) => c.id === checkpointId)?.mode ?? "AUTO";
  const ttl = (db.settings.presenceTtlHours ?? 16) * 3600000;
  const p = presenceNow(db).find((x) => x.workerId === workerId);
  const direction = mode !== "AUTO" ? mode : p && now - p.since < ttl ? "OUT" : "IN";
  return { direction, repeat: false, shift: shiftFor(db, workerId) };
};

export type { Device, AccessEvent };
