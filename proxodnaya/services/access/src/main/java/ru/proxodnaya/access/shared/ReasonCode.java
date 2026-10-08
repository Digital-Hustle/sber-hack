package ru.proxodnaya.access.shared;

/**
 * Коды результата прохода (docs/API.md «Коды отказов прохода», docs/BACKEND-CONTRACT.md).
 * Первая проваленная проверка DecisionEngine и есть причина.
 */
public enum ReasonCode {
    OK("Проходите", "Хорошей смены"),
    QR_MISSING("QR-код не распознан", "Покажите QR из приложения"),
    QR_INVALID("Недействительный пропуск", "Откройте приложение «Проходная»"),
    QR_EXPIRED("QR устарел или часы телефона сбиты", "Подождите новый QR; проверьте время на телефоне"),
    QR_REUSED("Этот QR уже использован", "Дождитесь следующего QR"),
    REPEAT_SCAN("Проход уже зарегистрирован", "Повторно предъявить пропуск можно через 30 секунд"),
    DEVICE_UNKNOWN("Телефон не привязан", Hints.ADMIN),
    DEVICE_REVOKED("Телефон отвязан", Hints.ADMIN),
    DEVICE_MISMATCH("Пропуск с чужого устройства", Hints.ADMIN),
    WORKER_BLOCKED("Доступ заблокирован", Hints.ADMIN),
    FACE_NOT_ENROLLED("Нет подтверждённого фото", "Пройдите через охранника"),
    FACE_NOT_FOUND("Лицо не найдено", "Встаньте напротив камеры"),
    FACE_MULTIPLE("В кадре несколько человек", "Подходите по одному"),
    FACE_LOW_QUALITY("Плохое качество кадра", "Подойдите ближе, снимите очки или капюшон"),
    FACE_MISMATCH("Лицо не совпадает с владельцем пропуска", "Проход только по своему пропуску"),
    LIVENESS_FAILED("Проверка не пройдена", "Выполните действие на экране"),
    CHALLENGE_EXPIRED("Время вышло", "Начните заново"),
    NO_SHIFT("Нет смены на сегодня", Hints.FOREMAN),
    OUTSIDE_SHIFT_WINDOW("Вне времени смены", "Подойдите ко времени начала смены"),
    PERMIT_EXPIRED("Просрочен инструктаж или медосмотр", "Обратитесь к ответственному по ТБ"),
    NO_ZONE_PERMIT("Нет допуска в эту зону", Hints.FOREMAN),
    ALREADY_INSIDE("Вы уже на объекте", "Сначала отметьте выход"),
    NOT_INSIDE("Вход не отмечен", "Отметьте вход"),
    TEMP_LOCKED("Слишком много неудачных попыток", Hints.GUARD),
    SYSTEM_ERROR("Система недоступна, проход закрыт", Hints.GUARD);

    private final String message;
    private final String hint;

    ReasonCode(String message, String hint) {
        this.message = message;
        this.hint = hint;
    }

    /**
     * Текст для экрана киоска.
     *
     * @return сообщение по-русски
     */
    public String message() {
        return message;
    }

    /**
     * Что делать человеку у турникета.
     *
     * @return подсказка по-русски
     */
    public String hint() {
        return hint;
    }

    /**
     * Повторяющиеся подсказки.
     */
    private static final class Hints {
        static final String ADMIN = "Обратитесь к администратору";
        static final String FOREMAN = "Обратитесь к прорабу";
        static final String GUARD = "Обратитесь к охране";

        private Hints() {
        }
    }
}
