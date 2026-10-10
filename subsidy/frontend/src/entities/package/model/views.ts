import type { Tone } from "@/shared/ui";
import type { PackageStatus, ProtocolStatus } from "./types";

/** Подписи кодов с бэка (docs/API.md §Стадии, §Причины). Вердикты не вычисляем — только показываем. */
export const PACKAGE_STATUS: Record<PackageStatus, { label: string; tone: Tone }> = {
  COLLECTING: { label: "Собирается", tone: "neutral" },
  PROCESSING: { label: "Разбирается", tone: "info" },
  CHECKED: { label: "Проверен", tone: "success" },
  DECIDED: { label: "Решение принято", tone: "neutral" },
};

export const PROTOCOL_STATUS: Record<ProtocolStatus, { label: string; tone: Tone }> = {
  COMPLETE: { label: "Комплект полный", tone: "success" },
  INCOMPLETE: { label: "Не хватает документов", tone: "danger" },
  NEEDS_REVIEW: { label: "Нужна ручная проверка", tone: "warning" },
};

export const STAGE: Record<string, { label: string; tone: Tone; busy?: boolean }> = {
  RECEIVED: { label: "Файл принят", tone: "neutral", busy: true },
  QUEUED: { label: "В очереди на разбор", tone: "neutral", busy: true },
  EXTRACTING_TEXT: { label: "Читаем текст", tone: "info", busy: true },
  CLASSIFYING: { label: "Определяем документы", tone: "info", busy: true },
  SEGMENTED: { label: "Документы найдены", tone: "info", busy: true },
  EXTRACTING_FIELDS: { label: "Извлекаем данные", tone: "info", busy: true },
  VALIDATING: { label: "Проверяем реквизиты", tone: "info", busy: true },
  PARSED: { label: "Разобран", tone: "success" },
  NEEDS_REVIEW: { label: "Есть что проверить", tone: "warning" },
  UNREADABLE: { label: "Не удалось прочитать", tone: "danger" },
  WAITING_MODEL: { label: "Ждём модель", tone: "warning", busy: true },
  FAILED: { label: "Не удалось разобрать", tone: "danger" },
};
export const stageView = (stage: string) => STAGE[stage] ?? { label: stage, tone: "neutral" as Tone };

export const REASON: Record<string, string> = {
  IMAGE_BLURRY: "Скан размыт — переснимите",
  IMAGE_TOO_DARK: "Скан слишком тёмный — переснимите",
  IMAGE_CUT_OFF: "Скан обрезан — переснимите",
  TEXT_NOT_FOUND: "Не удалось найти текст",
  TYPE_UNKNOWN: "Вид документа не определён — выберите вручную",
  TYPE_OTHER: "Документ не входит в перечень",
  CHECKSUM_FAILED: "Реквизит не прошёл проверку контрольной суммы",
  DUPLICATE_DOCUMENT: "Такой документ уже есть в пакете",
  MODEL_UNAVAILABLE: "Модель недоступна, продолжим автоматически",
  MODEL_INVALID_OUTPUT: "Модель вернула ответ не по схеме — проверьте вручную",
  FIELDS_TO_CHECK: "Есть поля, которые стоит проверить",
};
export const reasonText = (code: string | null | undefined) => (code ? REASON[code] ?? code : null);

/** Подписи полей документов (docs/DOC-TYPES.md): специалист видит русские названия, а не ключи схемы. */
export const FIELD: Record<string, string> = {
  orgName: "Наименование организации",
  inn: "ИНН",
  kpp: "КПП",
  ogrn: "ОГРН",
  program: "Программа",
  requestedAmount: "Запрошенная сумма",
  date: "Дата",
  signerName: "Подписант",
  signerPosition: "Должность подписанта",
  bankAccount: "Расчётный счёт",
  bik: "БИК",
  extractDate: "Дата выписки",
  status: "Состояние",
  headName: "Руководитель",
  headPosition: "Должность руководителя",
  okvedMain: "Основной ОКВЭД",
  okvedExtra: "Дополнительные ОКВЭД",
  address: "Адрес",
  registrationDate: "Дата регистрации",
  certificateDate: "Дата справки",
  asOfDate: "По состоянию на",
  hasDebt: "Есть задолженность",
  debtAmount: "Сумма задолженности",
  ifnsCode: "Код ИФНС",
  period: "Отчётный период",
  revenue: "Выручка",
  netProfit: "Чистая прибыль",
  assetsTotal: "Активы",
  liabilitiesTotal: "Обязательства",
  equity: "Капитал",
  items: "Позиции",
  total: "Итого",
  rate: "Ставка субсидии",
  subsidyAmount: "Сумма субсидии",
  number: "Номер",
  buyerName: "Покупатель",
  buyerInn: "ИНН покупателя",
  sellerName: "Поставщик",
  sellerInn: "ИНН поставщика",
  subject: "Предмет",
  amount: "Сумма",
  amountWords: "Сумма прописью",
  vatAmount: "НДС",
  kind: "Вид документа",
  totalWords: "Итого прописью",
  contractRef: "Ссылка на договор",
  payerName: "Плательщик",
  payerInn: "ИНН плательщика",
  payerAccount: "Счёт плательщика",
  payerBik: "БИК банка плательщика",
  payeeName: "Получатель",
  payeeInn: "ИНН получателя",
  payeeAccount: "Счёт получателя",
  payeeBik: "БИК банка получателя",
  purpose: "Назначение платежа",
  summary: "Описание",
};
export const fieldLabel = (name: string) => FIELD[name] ?? name;

/** Значение поля как строка: деньги и даты приходят строками и показываются как есть. */
export const fieldText = (v: unknown): string => {
  if (v === null || v === undefined || v === "") return "—";
  if (typeof v === "string" || typeof v === "number" || typeof v === "boolean") return v === true ? "да" : v === false ? "нет" : String(v);
  if (Array.isArray(v)) return `${v.length} строк`;
  return JSON.stringify(v);
};
