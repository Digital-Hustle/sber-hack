package ru.proxodnaya.shift.features.analytics.query.model;

import java.time.Instant;
import java.util.List;

/**
 * Отказы на проходной за период (B-07).
 *
 * @param from       начало
 * @param to         конец, не включая
 * @param attempts   всего попыток
 * @param denied     из них отказов
 * @param system     отказов по вине системы (кадр, сбой)
 * @param legitimate правомерных отказов
 * @param byReason   по причинам, больше — выше
 * @param method     как посчитано
 */
public record RefusalStats(Instant from, Instant to, long attempts, long denied, long system, long legitimate,
                           List<ReasonCount> byReason, String method) {

    /**
     * Причина отказа.
     *
     * @param reasonCode код причины access
     * @param count      отказов
     * @param system     системная (не вина человека)
     */
    public record ReasonCount(String reasonCode, long count, boolean system) {
    }
}
