import type { Tone } from "@/shared/ui";

const STATUS: Record<string, { label: string; tone: Tone }> = {
  DRAFT: { label: "Собирается", tone: "neutral" },
  SUBMITTED: { label: "Ждёт проверки", tone: "info" },
  IN_REVIEW: { label: "На проверке", tone: "warning" },
  NEEDS_CHANGES: { label: "Ждём документы", tone: "warning" },
  ACCEPTED: { label: "Принята", tone: "success" },
  RETURNED: { label: "Возвращена", tone: "danger" },
};
export const statusView = (s?: string | null) => STATUS[s ?? ""] ?? { label: s ?? "—", tone: "neutral" as Tone };

/** Состояние пункта комплекта (intake: MISSING | PROCESSING | DONE | PROBLEM). */
const SLOT: Record<string, { label: string; tone: Tone }> = {
  MISSING: { label: "Не загружен", tone: "neutral" },
  PROCESSING: { label: "Проверяем", tone: "info" },
  DONE: { label: "Готово", tone: "success" },
  PROBLEM: { label: "Нужно переснять", tone: "danger" },
};
export const slotView = (s?: string | null) => SLOT[s ?? ""] ?? SLOT.MISSING;

const ACTIONS: Record<string, string> = {
  DOCUMENT_UPLOADED: "Загружен документ",
  DOCUMENT_REMOVED: "Документ удалён",
  FILES_CHANGED: "Файлы документа изменены",
  DOCUMENT_PARSED: "Документ разобран",
  SUBMITTED: "Заявка отправлена",
  ASSIGNED: "Назначена оператору",
  FIELD_CORRECTED: "Исправлено поле",
  TYPE_CORRECTED: "Изменён вид документа",
  DOCUMENT_CONFIRMED: "Документ подтверждён",
  REPROCESS_REQUESTED: "Запрошен повторный разбор",
  RESUBMIT_REQUESTED: "Запрошены документы у соискателя",
  DECIDED: "Решение по заявке",
  REVEALED: "Показан полный номер",
  EXPORTED: "Выгружена карточка",
};
export const actionLabel = (a?: string | null) => ACTIONS[a ?? ""] ?? a ?? "—";

const SOURCES: Record<string, string> = { APPLICANT: "соискатель", OPERATOR: "оператор", MODEL: "модель", SYSTEM: "система" };
export const sourceLabel = (s?: string | null) => SOURCES[s ?? ""] ?? s ?? "";

/** Секунды ожидания → «2 ч 15 мин». */
export const waitingRu = (sec?: number | null) => {
  if (sec == null) return "—";
  const m = Math.floor(sec / 60);
  if (m < 1) return "меньше минуты";
  if (m < 60) return `${m} мин`;
  const h = Math.floor(m / 60);
  if (h < 48) return `${h} ч ${m % 60} мин`;
  return `${Math.floor(h / 24)} дн`;
};

export const dateTimeRu = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—";
export const dateRuFull = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" }) : "—";
