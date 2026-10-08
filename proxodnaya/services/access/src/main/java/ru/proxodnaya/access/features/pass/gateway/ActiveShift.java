package ru.proxodnaya.access.features.pass.gateway;

import java.time.Instant;

/**
 * Активная смена рабочего из shift (/internal/shift/workers/{id}/active-shift).
 *
 * @param shiftId   id смены
 * @param start     начало
 * @param end       конец
 * @param windowOk  текущее время в окне
 */
public record ActiveShift(
        String shiftId,
        Instant start,
        Instant end,
        boolean windowOk
) {}
