package ru.proxodnaya.shift.features.interval.model;

/**
 * Почему интервал присутствия закрыт.
 */
public enum CloseReason {
    /** Отмечен выход. */
    EXIT,
    /** Вход в другую зону через внутреннюю проходную. */
    ZONE_CHANGE,
    /** Повторный вход без отмеченного выхода — конец интервала не подтверждён. */
    REENTRY,
    /** Выхода нет через {@code AUTO_CLOSE_AFTER} после конца смены — закрыт по концу смены, не подтверждён. */
    AUTO_SHIFT_END
}
