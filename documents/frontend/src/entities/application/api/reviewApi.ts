import { api, download } from "@/shared/api";
import type { Card, DocType, Expiring, HistoryEntry, Metrics, ModelStatus, QueuePage } from "../model/types";

export type QueueFilter = { status?: string; q?: string; assignee?: string; sort?: string };

const qs = (o: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : "";
};

/** Рабочее место оператора → review (+ docproc для справочников и статуса модели). */
export const reviewApi = {
  queue: (f: QueueFilter, cursor: string | null, limit = 30) => api<QueuePage>("review", `/applications${qs({ ...f, cursor, limit })}`),
  next: () => api<{ applicationId?: string } | undefined>("review", "/applications/next", { method: "POST" }),
  assign: (id: string, force = false) => api<void>("review", `/applications/${id}/assign${force ? "?force=true" : ""}`, { method: "POST" }),
  card: (id: string) => api<Card>("review", `/applications/${id}`),
  history: (id: string) => api<{ items?: HistoryEntry[] }>("review", `/applications/${id}/history`),
  request: (id: string, items: { documentId?: string; type?: string; reason: string }[]) =>
    api<void>("review", `/applications/${id}/request`, { method: "POST", body: { items } }),
  decide: (id: string, decision: "ACCEPTED" | "RETURNED", comment?: string) =>
    api<void>("review", `/applications/${id}/decision`, { method: "POST", body: { decision, comment } }),
  reveal: (id: string, documentId: string, field: string) =>
    api<{ value?: string }>("review", `/applications/${id}/reveal`, { method: "POST", body: { documentId, field } }),
  exportCard: (id: string, format: "docx" | "xlsx" | "pdf") => download("review", `/applications/${id}/export?format=${format}`, `application-${id}.${format}`),
  correct: (docId: string, fields: { name: string; value: string }[]) =>
    api<void>("review", `/documents/${docId}/fields`, { method: "PATCH", body: { fields } }),
  retype: (docId: string, type: string) => api<void>("review", `/documents/${docId}/type`, { method: "PATCH", body: { type } }),
  confirm: (docId: string) => api<void>("review", `/documents/${docId}/confirm`, { method: "POST" }),
  reprocess: (docId: string) => api<void>("review", `/documents/${docId}/reprocess`, { method: "POST" }),
  expiring: (days: number) => api<Expiring>("review", `/reports/expiring${qs({ days })}`),
  exportExpiring: (days: number) => download("review", `/reports/expiring/export${qs({ days })}`, `expiring-${days}.xlsx`),
  metrics: () => api<Metrics>("review", "/metrics"),
  types: () => api<{ items?: DocType[] }>("docproc", "/types"),
  model: () => api<ModelStatus>("docproc", "/model/status"),
};

export const reviewKeys = {
  queue: (f: QueueFilter) => ["review", "queue", f] as const,
  queueAll: ["review", "queue"] as const,
  card: (id: string) => ["review", "card", id] as const,
  history: (id: string) => ["review", "history", id] as const,
  expiring: (days: number) => ["review", "expiring", days] as const,
  metrics: ["review", "metrics"] as const,
  types: ["docproc", "types"] as const,
  model: ["docproc", "model"] as const,
};
