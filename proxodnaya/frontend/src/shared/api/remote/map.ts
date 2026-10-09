// Ответы сервисов → модель фронта (types.ts). Время сервера — ISO или Unix ms, у фронта — ms.
import type { AccessEvent, AdminUser, Attempt, Checkpoint, Kiosk, ReasonCode, Settings, Shift, Site, Worker, Zone } from "../types";
import { ms } from "./http";
import { hhmm, todayKey } from "../../lib/time";

type Ts = string | number | null | undefined;

export type WorkerRs = {
  id: string; fullName: string; position?: string; contractor?: string; status: string; zoneIds?: string[]; permitUntil?: string | null;
  faceEnrolledAt?: Ts; createdAt?: Ts; photo?: string | null;
  face?: { status: "NONE" | "PENDING" | "ACTIVE" | "REJECTED"; source?: "HR" | "PHONE" | "KIOSK"; at?: Ts; by?: string; comment?: string; pendingPhoto?: string; dupOf?: string } | null;
};
export const toWorker = (w: WorkerRs): Worker => ({
  id: w.id, fullName: w.fullName, position: w.position ?? "", contractor: w.contractor ?? "",
  status: w.status === "BLOCKED" ? "blocked" : "active", zoneIds: w.zoneIds ?? [], permitUntil: w.permitUntil ?? "",
  createdAt: ms(w.createdAt) ?? 0, photo: w.photo ?? undefined,
  face: w.face
    ? { status: w.face.status, source: w.face.source, at: ms(w.face.at), by: w.face.by, comment: w.face.comment, pendingPhoto: w.face.pendingPhoto, dupOf: w.face.dupOf }
    : w.faceEnrolledAt ? { status: "ACTIVE", source: "HR", at: ms(w.faceEnrolledAt) } : { status: "NONE" },
});

export const toSite = (s: { id: string; name: string; address?: string | null }): Site => ({ id: s.id, name: s.name, address: s.address ?? undefined });
export const toZone = (z: { id: string; siteId?: string; name: string; capacity?: number | null }): Zone => ({ id: z.id, siteId: z.siteId, name: z.name, capacity: z.capacity ?? 0 });
export const toCheckpoint = (c: { id: string; zoneId: string; name: string; mode?: string | null }): Checkpoint =>
  ({ id: c.id, zoneId: c.zoneId, name: c.name, mode: (c.mode as Checkpoint["mode"]) ?? "AUTO" });

export type AttemptRs = {
  id: string; ts: Ts; checkpointId: string; zoneId?: string; direction: "IN" | "OUT"; workerId?: string | null; decision: Attempt["decision"]; code: ReasonCode;
  source: string; score?: number | null; note?: string | null; guardId?: string | null; guard?: string | null; message?: string;
  review?: { status: "CONFIRMED" | "DISPUTED"; by: string; at: Ts; comment?: string } | null;
  offline?: { kioskId: string; syncedAt: Ts; conflict?: string; seq?: number; signed?: boolean } | null;
};
export const toAttempt = (a: AttemptRs): Attempt => ({
  id: a.id, ts: ms(a.ts) ?? 0, workerId: a.workerId ?? undefined, checkpointId: a.checkpointId, direction: a.direction, decision: a.decision, code: a.code,
  source: (["QR", "MANUAL", "FACE", "OFFLINE"].includes(a.source) ? a.source : "QR") as Attempt["source"],
  score: a.score ?? undefined, note: a.note ?? undefined, guard: a.guard ?? a.guardId ?? undefined,
  review: a.review ? { status: a.review.status, by: a.review.by, at: ms(a.review.at) ?? 0, comment: a.review.comment } : undefined,
  offline: a.offline ? { kioskId: a.offline.kioskId, syncedAt: ms(a.offline.syncedAt) ?? 0, conflict: a.offline.conflict, seq: a.offline.seq, signed: a.offline.signed } : undefined,
});

export type ShiftRs = { id: string; workerId: string; start: Ts; end: Ts };
export const toShift = (s: ShiftRs): Shift => {
  const start = ms(s.start) ?? 0, end = ms(s.end) ?? 0;
  return { id: s.id, workerId: s.workerId, day: todayKey(new Date(start)), start: hhmm(start), end: hhmm(end) };
};

export type SettingsRs = Settings & { updatedAt?: number; updatedBy?: string };
export const toSettings = (s: SettingsRs): Settings => s;

export type KioskRs = {
  id: string; name?: string; checkpointId?: string; mode?: Kiosk["mode"]; offlinePolicy?: Kiosk["offlinePolicy"]; pairedAt?: Ts; lastSeen?: Ts; snapshotAt?: Ts;
  chain?: { seq: number; hash: string }; syncError?: { at: Ts; message: string } | null; pairCode?: string;
};
export const toKiosk = (k: KioskRs): Kiosk => ({
  id: k.id, pairCode: k.pairCode ?? "", name: k.name, checkpointId: k.checkpointId, mode: k.mode, offlinePolicy: k.offlinePolicy,
  pairedAt: ms(k.pairedAt), lastSeen: ms(k.lastSeen) ?? 0, snapshotAt: ms(k.snapshotAt), chain: k.chain,
  syncError: k.syncError ? { at: ms(k.syncError.at) ?? 0, message: k.syncError.message } : undefined, createdAt: ms(k.pairedAt) ?? 0,
});

export type StaffUserRs = { id: string; name: string; email: string; role: AdminUser["role"]; status: AdminUser["status"]; lastSeen?: number | null; createdAt?: Ts };
export const toAdmin = (u: StaffUserRs): AdminUser => ({ id: u.id, name: u.name, email: u.email, role: u.role, status: u.status, createdAt: ms(u.createdAt) ?? 0, lastSeen: u.lastSeen ?? undefined });

export type AccessEntryRs = { id: string; ts: Ts; by?: string; byId?: string | null; target?: string; targetId?: string | null; action: AccessEvent["action"];
  from?: AccessEvent["from"] | null; to?: AccessEvent["to"] | null; code?: AccessEvent["code"] | null; detail?: string | null };
export const toAccessEvent = (e: AccessEntryRs): AccessEvent => ({
  id: e.id, ts: ms(e.ts) ?? 0, by: e.byId ?? e.by ?? "", target: e.targetId ?? e.target ?? "", action: e.action,
  from: e.from ?? undefined, to: e.to ?? undefined, code: e.code ?? undefined, detail: e.detail ?? undefined,
});
