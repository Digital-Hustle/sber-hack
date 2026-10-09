import { REASONS, type DecisionResult, type Direction, type OfflineEvent, type OfflineSnapshot, type ReasonCode } from "@/shared/api";
import { atTime, currentWindow, parsePassQr, randomId, signedMessage, todayKey, verify, QR_WINDOW_SEC } from "@/shared/lib";
import { useOfflinePass } from "./store";

type SnapWorker = OfflineSnapshot["workers"][number];

const toResult = (e: OfflineEvent, w?: SnapWorker): DecisionResult => {
  const allow = e.decision === "ALLOW";
  const r = REASONS[e.code];
  return {
    attemptId: e.id, decision: e.decision, code: e.code,
    message: allow ? (e.direction === "IN" ? "Проходите" : "Хорошего вечера") : r.message,
    hint: allow ? "Проверено терминалом без связи · попадёт в журнал, когда связь вернётся" : r.hint,
    worker: w ? { id: w.id, fullName: w.fullName, position: w.position, contractor: w.contractor } : undefined,
    direction: e.direction, ts: e.ts,
  };
};

/**
 * Автономная проверка QR на терминале (ADR-042). Закрытый ключ есть только в телефоне сотрудника,
 * у терминала — открытые ключи, список отозванных телефонов, допуски и смены из последнего снимка. Подпись,
 * окно времени и одноразовость проверяются без сервера; каждое решение встаёт в очередь на синхронизацию.
 */
export const verifyLocal = async (raw: string, now = Date.now()): Promise<DecisionResult> => {
  const { snapshot: s, queue, used, push } = useOfflinePass.getState();
  const id = randomId("off");
  const ev = (code: ReasonCode, direction: Direction, workerId?: string, extra?: Partial<OfflineEvent>): OfflineEvent =>
    ({ id, ts: now, workerId, checkpointId: s?.checkpoint.id ?? "cp_main", direction, decision: REASONS[code].decision, code, ...extra });
  // Исходный QR прикладывается к записи, как только он разобран (до этого доказывать нечего).
  const ctx: { proof?: string } = {};
  const done = async (e: OfflineEvent, w?: SnapWorker) => toResult(await push(ctx.proof ? { ...e, proof: ctx.proof } : e), w);

  if (!s) return done(ev("OFFLINE_EXPIRED", "IN"));
  const cp = s.checkpoint;
  const fixed: Direction | null = cp.mode === "IN" || cp.mode === "OUT" ? cp.mode : null;
  if (now - s.at > s.rules.maxHours * 3600000) return done(ev("OFFLINE_EXPIRED", fixed ?? "IN"));

  const qr = parsePassQr(raw);
  if (!qr) return done(ev("QR_INVALID", fixed ?? "IN"));
  ctx.proof = raw.trim();
  const w = s.workers.find((x) => x.id === qr.workerId);
  if (!w) return done(ev("DEVICE_UNKNOWN", fixed ?? "IN"));

  // Присутствие: кто был внутри на момент снимка + проходы, уже отмеченные этим терминалом без связи.
  const mine = queue.filter((e) => e.workerId === w.id && e.decision === "ALLOW");
  const last = mine[mine.length - 1];
  const inside = last ? last.direction === "IN" : s.inside.includes(w.id);
  const direction: Direction = fixed ?? (inside ? "OUT" : "IN");

  // Ключ телефона — только из снимка: неизвестный или отозванный телефон не проходит.
  const dev = s.devices.find((d) => d.id === qr.deviceId);
  if (!dev) return done(ev("DEVICE_UNKNOWN", direction, w.id), w);
  if (dev.workerId !== w.id) return done(ev("DEVICE_MISMATCH", direction, w.id), w);
  if (!(await verify(dev.publicKey, qr.signature, signedMessage(qr.workerId, qr.deviceId, qr.window)))) return done(ev("QR_INVALID", direction, w.id), w);
  const nowW = currentWindow(now);
  if (qr.window > nowW + 1 || (nowW - qr.window) * QR_WINDOW_SEC > s.rules.qrToleranceSec) return done(ev("QR_EXPIRED", direction, w.id), w);
  const useKey = `${qr.deviceId}|${qr.window}`;
  if (used.includes(useKey)) return done(ev("QR_REUSED", direction, w.id), w);
  if (dev.revokedAt) return done(ev("DEVICE_REVOKED", direction, w.id, { useKey }), w);

  const day = todayKey(new Date(now));
  if (w.status === "blocked") return done(ev("WORKER_BLOCKED", direction, w.id, { useKey }), w);
  if (w.permitUntil < day) return done(ev("PERMIT_EXPIRED", direction, w.id, { useKey }), w);
  if (cp.zoneId && !w.zoneIds.includes(cp.zoneId)) return done(ev("NO_ZONE_PERMIT", direction, w.id, { useKey }), w);
  if (last && now - last.ts < s.rules.repeatScanCooldownSec * 1000) return done(ev("REPEAT_SCAN", direction, w.id, { useKey }), w);
  if (direction === "IN") {
    if (s.rules.requireShift && s.rules.checkShift) {
      const sh = s.shifts.find((x) => x.workerId === w.id && x.day === day);
      if (!sh) return done(ev("NO_SHIFT", direction, w.id, { useKey }), w);
      const g = s.rules.shiftGraceMin * 60000;
      if (now < atTime(sh.day, sh.start) - g || now > atTime(sh.day, sh.end)) return done(ev("OUTSIDE_SHIFT_WINDOW", direction, w.id, { useKey }), w);
    }
    if (inside && fixed === "IN") return done(ev("ALREADY_INSIDE", direction, w.id, { useKey }), w);
  } else if (!inside) return done(ev("NOT_INSIDE", direction, w.id, { useKey }), w);

  return done(ev("OK", direction, w.id, { useKey }), w);
};
