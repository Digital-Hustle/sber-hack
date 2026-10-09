import type { Tone } from "@/shared/ui";

/** Стадии документа — тексты из docs/API.md §Стадии (соискателю — простыми словами). */
const STAGES: Record<string, { label: string; tone: Tone; done?: boolean; problem?: boolean }> = {
  RECEIVED: { label: "Файл принят", tone: "info" },
  QUEUED: { label: "В очереди на разбор", tone: "info" },
  EXTRACTING_TEXT: { label: "Читаем текст", tone: "info" },
  CLASSIFYING: { label: "Определяем, что это за документ", tone: "info" },
  EXTRACTING_FIELDS: { label: "Извлекаем данные", tone: "info" },
  VALIDATING: { label: "Проверяем данные", tone: "info" },
  PARSED: { label: "Документ разобран", tone: "success", done: true },
  NEEDS_REVIEW: { label: "Разобран, проверит оператор", tone: "warning", done: true },
  UNREADABLE: { label: "Не удалось прочитать", tone: "danger", done: true, problem: true },
  WAITING_MODEL: { label: "Разбор приостановлен, продолжим автоматически", tone: "warning" },
  FAILED: { label: "Не удалось разобрать, оператор проверит вручную", tone: "danger", done: true, problem: true },
};
const ORDER = ["RECEIVED", "QUEUED", "EXTRACTING_TEXT", "CLASSIFYING", "EXTRACTING_FIELDS", "VALIDATING", "PARSED"];

export const stageView = (stage?: string | null) => {
  const s = STAGES[stage ?? ""] ?? { label: stage ?? "—", tone: "neutral" as Tone };
  const i = ORDER.indexOf(stage ?? "");
  return { ...s, done: !!s.done, problem: !!s.problem, progress: s.done ? 1 : i < 0 ? 0.5 : (i + 1) / ORDER.length };
};

/** Причины непригодности и замечания — docs/API.md §Причины. Параметры подставляются в текст. */
const REASONS: Record<string, [string, string?]> = {
  IMAGE_BLURRY: ["Снимок размыт", "Держите телефон неподвижно, наведите фокус"],
  IMAGE_TOO_DARK: ["Слишком темно", "Снимите при хорошем свете, без вспышки"],
  IMAGE_GLARE: ["Блик закрывает текст", "Наклоните документ, уберите блик"],
  IMAGE_CUT_OFF: ["Документ обрезан", "Весь документ должен быть в кадре"],
  NOT_A_DOCUMENT: ["На файле нет документа", "Загрузите фото или скан документа"],
  TEXT_NOT_FOUND: ["Не удалось найти текст", "Загрузите более чёткий снимок"],
  TYPE_UNKNOWN: ["Не удалось определить вид документа", "Оператор проверит вручную"],
  TYPE_MOVED: ["Это другой документ — перенесли в пункт «{type}»"],
  DOCUMENT_EXPIRED: ["Срок действия документа истёк {date}", "Загрузите действующий документ"],
  CHECKSUM_FAILED: ["Номер не прошёл проверку ({field})", "Оператор сверит с оригиналом"],
  MRZ_MISMATCH: ["Данные в машиночитаемой строке не совпадают с текстом", "Оператор сверит с оригиналом"],
  CROSS_MISMATCH: ["{field} отличается в документах «{a}» и «{b}»", "Оператор уточнит"],
  NUMBER_ELSEWHERE: ["Номер из документа «{doc}» уже есть в другой заявке (заявок: {count})", "Проверьте, не повторная ли это подача или чужой документ"],
  MULTI_DOCUMENT: ["В одном файле несколько документов: здесь стр. {pages}, а {documents} — отдельными пунктами", "Мы разделили файл сами — проверьте, что каждый документ попал в свой пункт"],
  MISSING_DOCUMENT: ["Не хватает документа «{type}»", "Запросите у соискателя"],
  MODEL_UNAVAILABLE: ["Модель недоступна", "Продолжим автоматически"],
  MODEL_INVALID_OUTPUT: ["Модель вернула ответ не по схеме", "Оператор проверит вручную"],
};

const fill = (t: string, p: Record<string, unknown>) => t.replace(/\{(\w+)\}/g, (_, k: string) => (p[k] == null ? "" : String(p[k])));

export const reasonView = (code?: string | null, params: Record<string, unknown> = {}) => {
  if (!code) return null;
  const [text, hint] = REASONS[code] ?? [code];
  return { code, text: fill(text, params), hint: hint && fill(hint, params) };
};

/** Проверки поля (docproc checks[].code) — короткие подписи для оператора. */
const CHECKS: Record<string, string> = {
  DATE_RANGE: "дата в допустимом диапазоне",
  NOT_IN_FUTURE: "дата не в будущем",
  NOT_EXPIRED: "срок не истёк",
  AFTER_BIRTH: "позже даты рождения",
  AFTER_ISSUE: "позже даты выдачи",
  AGE_AT_ISSUE: "возраст на дату выдачи",
  INN_CHECKSUM: "контрольная сумма ИНН",
  SNILS_CHECKSUM: "контрольная сумма СНИЛС",
  MRZ_CHECKSUM: "контрольные цифры MRZ",
  MRZ_MATCH: "совпадает с MRZ",
  FORMAT: "формат",
};
export const checkLabel = (code: string) => CHECKS[code] ?? code.toLowerCase().replace(/_/g, " ");

/** Уверенность 0..1 → тон: ниже порога — «перепроверить». */
export const confidenceTone = (c?: number | null): Tone => (c == null ? "neutral" : c >= 0.85 ? "success" : c >= 0.6 ? "warning" : "danger");
export const percent = (c?: number | null) => (c == null ? "—" : `${Math.round(c * 100)}%`);
