// Производные данные: присутствие, интервалы, отработанное время, аналитика.
// Считается по кэшу последних проходов (store.ts); точные отчёты — /shift/api/v1/analytics/* и табель.
import type { Attempt, Db, Interval, ReasonCode, Shift, Site } from "../types";
import { atTime, dayKey, todayKey } from "../../lib/time";
import { SYSTEM_CODES } from "./reasons";

const isPass = (a: Attempt) => a.decision === "ALLOW" || a.decision === "MANUAL";
const zoneOf = (db: Db, checkpointId: string) => db.checkpoints.find((c) => c.id === checkpointId)?.zoneId ?? db.removedCheckpoints?.[checkpointId]?.zoneId ?? "";

export const buildIntervals = (db: Db): Interval[] => {
  const open = new Map<string, Interval>();
  const out: Interval[] = [];
  for (const a of db.attempts) {
    if (!a.workerId || !isPass(a)) continue;
    if (a.direction === "IN") {
      const prev = open.get(a.workerId);
      if (prev) prev.end = a.ts; // вход без выхода — закрываем предыдущий
      const iv: Interval = { workerId: a.workerId, zoneId: zoneOf(db, a.checkpointId), start: a.ts };
      open.set(a.workerId, iv);
      out.push(iv);
    } else {
      const iv = open.get(a.workerId);
      if (iv) { iv.end = a.ts; open.delete(a.workerId); }
    }
  }
  return out;
};

export type Presence = { workerId: string; zoneId: string; since: number };

export const presenceNow = (db: Db): Presence[] =>
  buildIntervals(db).filter((i) => i.end === undefined).map((i) => ({ workerId: i.workerId, zoneId: i.zoneId, since: i.start }));

export const shiftFor = (db: Db, workerId: string, day = todayKey()): Shift | undefined =>
  db.shifts.find((s) => s.workerId === workerId && s.day === day);

/** worked = Σ |[entry, exit] ∩ [shift_start − g, shift_end + g]| */
export const workedMs = (db: Db, workerId: string, day: string, intervals = buildIntervals(db), now = Date.now()) => {
  const sh = shiftFor(db, workerId, day);
  const g = db.settings.shiftGraceMin * 60000;
  const from = sh ? atTime(day, sh.start) - g : atTime(day, "00:00");
  const to = sh ? atTime(day, sh.end) + g : atTime(day, "23:59");
  return intervals
    .filter((i) => i.workerId === workerId)
    .reduce((sum, i) => sum + Math.max(0, Math.min(i.end ?? now, to) - Math.max(i.start, from)), 0);
};

export type DayStats = { day: string; attempts: number; allow: number; deny: number; denySystem: number; hours: number; late: number; overtime: number };

export const dailyStats = (db: Db, days: string[]): DayStats[] => {
  const intervals = buildIntervals(db);
  const tol = 5 * 60000;
  return days.map((day) => {
    const list = db.attempts.filter((a) => dayKey(a.ts) === day);
    const deny = list.filter((a) => a.decision === "DENY");
    let hours = 0, late = 0, overtime = 0;
    for (const w of db.workers) {
      const ms = workedMs(db, w.id, day, intervals);
      hours += ms / 3600000;
      const sh = shiftFor(db, w.id, day);
      const mine = list.filter((a) => a.workerId === w.id && isPass(a));
      const firstIn = mine.find((a) => a.direction === "IN");
      const lastOut = [...mine].reverse().find((a) => a.direction === "OUT");
      if (sh && firstIn && firstIn.ts - atTime(day, sh.start) > tol) late++;
      if (sh && lastOut && lastOut.ts - atTime(day, sh.end) > tol) overtime++;
    }
    return {
      day, attempts: list.length, allow: list.filter(isPass).length, deny: deny.length,
      denySystem: deny.filter((a) => SYSTEM_CODES.has(a.code)).length,
      hours: Math.round(hours * 10) / 10, late, overtime,
    };
  });
};

export const refusalsByCode = (db: Db, fromTs: number) => {
  const m = new Map<ReasonCode, number>();
  for (const a of db.attempts) if (a.ts >= fromTs && a.decision === "DENY") m.set(a.code, (m.get(a.code) ?? 0) + 1);
  return [...m.entries()].map(([code, count]) => ({ code, count, system: SYSTEM_CODES.has(code) })).sort((a, b) => b.count - a.count);
};

export const loadByHour = (db: Db, fromTs: number) => {
  const hours = Array.from({ length: 24 }, (_, h) => ({ hour: h, in: 0, out: 0 }));
  for (const a of db.attempts) if (a.ts >= fromTs && isPass(a)) hours[new Date(a.ts).getHours()][a.direction === "IN" ? "in" : "out"]++;
  return hours;
};

export const lastDays = (n: number) => Array.from({ length: n }, (_, i) => { const d = new Date(); d.setDate(d.getDate() - (n - 1 - i)); return todayKey(d); });

/** ADR-037: срок «забытого выхода», ч. Совпадает с умолчанием сервера. */
export const presenceTtlHours = (db: Db) => db.settings.presenceTtlHours ?? 16;

/** Незакрытые интервалы: вход без выхода старше presenceTtlHours. В «на объекте» их не считаем — следующий скан будет входом. */
export const unclosedIntervals = (db: Db, now = Date.now()) => {
  const ttl = presenceTtlHours(db) * 3600000;
  return buildIntervals(db).filter((i) => i.end === undefined && now - i.start > ttl);
};

// ——— ADR-044: объекты ———
const NO_SITE: Site = { id: "", name: "Без объекта" };
/** Нет поля — база до ADR-044, берём объекты по умолчанию. Пустой список — администратор удалил все объекты (ADR-047). */
export const sitesOf = (db: Db): Site[] => db.sites ?? [];
export const siteOfZone = (db: Db, zoneId: string): Site => {
  const sites = sitesOf(db);
  const id = db.zones.find((z) => z.id === zoneId)?.siteId;
  return sites.find((s) => s.id === id) ?? sites[0] ?? NO_SITE;
};
export const siteOfCheckpoint = (db: Db, checkpointId: string) => siteOfZone(db, zoneOf(db, checkpointId));

/** Объекты, куда у сотрудника есть допуск, с его зонами. Порядок — как в списке объектов. */
export const workerSites = (db: Db, workerId: string) => {
  const w = db.workers.find((x) => x.id === workerId);
  if (!w) return [];
  return sitesOf(db)
    .map((site) => ({ site, zones: db.zones.filter((z) => w.zoneIds.includes(z.id) && siteOfZone(db, z.id).id === site.id) }))
    .filter((x) => x.zones.length);
};

/** Первый вход и последний выход за день — для карточки смены. */
export const dayPasses = (db: Db, workerId: string, day: string) => {
  const list = db.attempts.filter((a) => a.workerId === workerId && isPass(a) && dayKey(a.ts) === day);
  const firstIn = list.find((a) => a.direction === "IN");
  const lastOut = [...list].reverse().find((a) => a.direction === "OUT");
  return { firstIn: firstIn?.ts, lastOut: lastOut?.ts, checkpointId: firstIn?.checkpointId };
};
