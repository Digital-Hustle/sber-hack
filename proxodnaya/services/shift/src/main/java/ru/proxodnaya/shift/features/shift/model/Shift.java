package ru.proxodnaya.shift.features.shift.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Плановая смена рабочего (UTC).
 *
 * @param id         id
 * @param workerId   рабочий
 * @param start      начало
 * @param end        конец (после начала, не дольше 24 ч)
 * @param templateId шаблон, по которому создана, или {@code null}
 */
public record Shift(UUID id, String workerId, Instant start, Instant end, UUID templateId) {
}
