import type { Decision, ReasonCode } from "../types";

// Тексты для киоска приходят с сервера вместе с кодом (docs/API.md, «Коды отказов прохода»); здесь — запасные для журнала и офлайна.
export const REASONS: Record<ReasonCode, { decision: Decision; message: string; hint: string }> = {
  OK: { decision: "ALLOW", message: "Проходите", hint: "Хорошей смены" },
  QR_MISSING: { decision: "DENY", message: "QR-код не распознан", hint: "Покажите QR из приложения" },
  QR_INVALID: { decision: "DENY", message: "Недействительный пропуск", hint: "Откройте приложение «Проходная»" },
  QR_EXPIRED: { decision: "DENY", message: "QR устарел", hint: "Дождитесь нового QR и проверьте время на телефоне" },
  QR_REUSED: { decision: "DENY", message: "Этот QR уже использован", hint: "Каждый QR одноразовый — дождитесь следующего" },
  DEVICE_UNKNOWN: { decision: "DENY", message: "Телефон не привязан", hint: "Обратитесь к администратору" },
  DEVICE_REVOKED: { decision: "DENY", message: "Телефон отвязан", hint: "Обратитесь к администратору" },
  DEVICE_MISMATCH: { decision: "DENY", message: "Пропуск с чужого устройства", hint: "Обратитесь к администратору" },
  WORKER_BLOCKED: { decision: "DENY", message: "Доступ заблокирован", hint: "Обратитесь к администратору" },
  FACE_NOT_FOUND: { decision: "DENY", message: "Лицо не найдено", hint: "Встаньте напротив камеры" },
  FACE_NOT_ENROLLED: { decision: "DENY", message: "Лицо ещё не добавлено", hint: "Добавьте лицо в приложении или обратитесь к охраннику" },
  FACE_LOW_QUALITY: { decision: "DENY", message: "Плохое качество кадра", hint: "Подойдите ближе, снимите капюшон" },
  FACE_MISMATCH: { decision: "DENY", message: "Лицо не совпадает с владельцем пропуска", hint: "Проход только по своему пропуску" },
  LIVENESS_FAILED: { decision: "DENY", message: "Проверка живости не пройдена", hint: "Выполните действие на экране" },
  CHALLENGE_EXPIRED: { decision: "DENY", message: "Время вышло", hint: "Покажите QR ещё раз" },
  NO_SHIFT: { decision: "DENY", message: "Нет смены на сегодня", hint: "Обратитесь к прорабу" },
  OFFLINE_EXPIRED: { decision: "DENY", message: "Автономная проверка недоступна", hint: "Связи нет слишком долго — обратитесь к охраннику" },
  OUTSIDE_SHIFT_WINDOW: { decision: "DENY", message: "Вне времени смены", hint: "Проверьте график в приложении" },
  PERMIT_EXPIRED: { decision: "DENY", message: "Просрочен инструктаж или медосмотр", hint: "Обратитесь к ответственному по ТБ" },
  NO_ZONE_PERMIT: { decision: "DENY", message: "Нет допуска в эту зону", hint: "Обратитесь к прорабу" },
  ALREADY_INSIDE: { decision: "DENY", message: "Вы уже на объекте", hint: "Сначала отметьте выход" },
  NOT_INSIDE: { decision: "DENY", message: "Вход не отмечен", hint: "Сначала отметьте вход" },
  TEMP_LOCKED: { decision: "DENY", message: "Слишком много неудачных попыток", hint: "Обратитесь к охране" },
  MANUAL_GUARD: { decision: "MANUAL", message: "Пропущен охранником", hint: "Решение записано в журнал с причиной" },
  REPEAT_SCAN: { decision: "DENY", message: "Проход уже зарегистрирован", hint: "Повторно предъявить пропуск можно через 30 секунд" },
  FACE_MULTIPLE: { decision: "DENY", message: "В кадре несколько лиц", hint: "Подходите к терминалу по одному" },
  ZONE_FULL: { decision: "DENY", message: "В зоне нет мест", hint: "Дождитесь, пока кто-то выйдет, или обратитесь к прорабу" },
  KIOSK_NOT_PAIRED: { decision: "DENY", message: "Терминал не подключён", hint: "Обратитесь к инженеру" },
  SYSTEM_ERROR: { decision: "ERROR", message: "Система недоступна, проход закрыт", hint: "Обратитесь к охране" },
};

/** Отказы, которые говорят о качестве системы, а не о нарушителе (честная метрика отказов). */
export const SYSTEM_CODES = new Set<ReasonCode>(["FACE_NOT_FOUND", "FACE_LOW_QUALITY", "CHALLENGE_EXPIRED", "SYSTEM_ERROR", "QR_MISSING"]);
