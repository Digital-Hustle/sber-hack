package ru.proxodnaya.shift.features.analytics.query.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Смена против факта.
 *
 * @param shiftId         смена
 * @param workerId        рабочий
 * @param start           начало по графику
 * @param end             конец по графику
 * @param arrival         статус прихода
 * @param firstEntry      первый вход в окне смены или {@code null}
 * @param lastExit        последний выход (для открытого интервала — текущий момент) или {@code null}
 * @param lateMinutes     минут опоздания (0, если пришёл вовремя)
 * @param overtimeMinutes минут после конца смены (0, если выход не отмечен)
 * @param overtime        переработка не меньше порога
 * @param incomplete      выход не отмечен — переработку посчитать нельзя
 */
public record ShiftOutcome(UUID shiftId, String workerId, Instant start, Instant end, Arrival arrival, Instant firstEntry,
                           Instant lastExit, long lateMinutes, long overtimeMinutes, boolean overtime, boolean incomplete) {

    /**
     * Приход на смену.
     */
    public enum Arrival {
        /** Вошёл не позже начала + допуск. */
        ON_TIME,
        /** Вошёл позже начала + допуск. */
        LATE,
        /** Смена закончилась, входа не было. */
        ABSENT,
        /** Смена идёт, входа пока нет. */
        NOT_YET
    }
}
