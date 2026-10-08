package ru.proxodnaya.access.shared;

/**
 * Текст отказа для киоска: сообщение и подсказка, что делать.
 * Тексты приходят с сервера вместе с кодом (docs/API.md).
 */
public record ReasonView(
        String code,
        String message,
        String hint
) {
    public static ReasonView of(ReasonCode code) {
        return switch (code) {
            case OK -> new ReasonView("OK", "Проходите", "Хорошей смены");
            case QR_MISSING -> new ReasonView("QR_MISSING", "QR-код не распознан", "Покажите QR из приложения");
            case QR_INVALID -> new ReasonView("QR_INVALID", "Недействительный пропуск", "Откройте приложение «Проходная»");
            case QR_EXPIRED -> new ReasonView("QR_EXPIRED", "QR устарел", "Дождитесь нового QR и проверьте время на телефоне");
            case QR_REUSED -> new ReasonView("QR_REUSED", "Этот QR уже использован", "Каждый QR одноразовый — дождитесь следующего");
            case DEVICE_UNKNOWN -> new ReasonView("DEVICE_UNKNOWN", "Телефон не привязан", "Обратитесь к администратору");
            case DEVICE_REVOKED -> new ReasonView("DEVICE_REVOKED", "Телефон отвязан", "Обратитесь к администратору");
            case DEVICE_MISMATCH -> new ReasonView("DEVICE_MISMATCH", "Пропуск с чужого устройства", "Обратитесь к администратору");
            case WORKER_BLOCKED -> new ReasonView("WORKER_BLOCKED", "Доступ заблокирован", "Обратитесь к администратору");
            case FACE_NOT_FOUND -> new ReasonView("FACE_NOT_FOUND", "Лицо не найдено", "Встаньте напротив камеры");
            case FACE_MULTIPLE -> new ReasonView("FACE_MULTIPLE", "В кадре несколько человек", "Подходите по одному");
            case FACE_LOW_QUALITY -> new ReasonView("FACE_LOW_QUALITY", "Плохое качество кадра", "Подойдите ближе, снимите очки/капюшон");
            case FACE_MISMATCH -> new ReasonView("FACE_MISMATCH", "Лицо не совпадает с владельцем пропуска", "Проход только по своему пропуску");
            case LIVENESS_FAILED -> new ReasonView("LIVENESS_FAILED", "Проверка не пройдена", "Выполните действие на экране");
            case CHALLENGE_EXPIRED -> new ReasonView("CHALLENGE_EXPIRED", "Время вышло", "Начните заново");
            case NO_SHIFT -> new ReasonView("NO_SHIFT", "Нет смены на сегодня", "Обратитесь к прорабу");
            case OUTSIDE_SHIFT_WINDOW -> new ReasonView("OUTSIDE_SHIFT_WINDOW", "Вне времени смены", "Смена с HH:MM");
            case PERMIT_EXPIRED -> new ReasonView("PERMIT_EXPIRED", "Просрочен инструктаж/медосмотр", "Обратитесь к ответственному по ТБ");
            case NO_ZONE_PERMIT -> new ReasonView("NO_ZONE_PERMIT", "Нет допуска в эту зону", "Обратитесь к прорабу");
            case ALREADY_INSIDE -> new ReasonView("ALREADY_INSIDE", "Вы уже на объекте", "Сначала отметьте выход");
            case NOT_INSIDE -> new ReasonView("NOT_INSIDE", "Вход не отмечен", "Отметьте вход");
            case ZONE_FULL -> new ReasonView("ZONE_FULL", "Зона переполнена", "Подождите");
            case TEMP_LOCKED -> new ReasonView("TEMP_LOCKED", "Слишком много неудачных попыток", "Обратитесь к охране");
            case KIOSK_SIGNATURE_INVALID -> new ReasonView("KIOSK_SIGNATURE_INVALID", "Киоск не авторизован", "Обратитесь к администратору");
            case SYSTEM_ERROR -> new ReasonView("SYSTEM_ERROR", "Система недоступна, проход закрыт", "Обратитесь к охране");
        };
    }

    public static ReasonView of(String codeStr) {
        try {
            return of(ReasonCode.valueOf(codeStr));
        } catch (IllegalArgumentException e) {
            return new ReasonView(codeStr, "Неизвестная ошибка", "Обратитесь к администратору");
        }
    }
}
