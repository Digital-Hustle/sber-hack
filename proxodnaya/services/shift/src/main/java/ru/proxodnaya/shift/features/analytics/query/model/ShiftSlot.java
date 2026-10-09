package ru.proxodnaya.shift.features.analytics.query.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Плановая смена для сравнения с фактом.
 *
 * @param id       смена
 * @param workerId рабочий
 * @param start    начало
 * @param end      конец
 */
public record ShiftSlot(UUID id, String workerId, Instant start, Instant end) {
}
