package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Отказы по причинам.
 *
 * @param from        начало
 * @param to          конец, не включая
 * @param attempts    всего попыток
 * @param denied      отказов
 * @param deniedShare доля отказов от попыток, 0..1
 * @param system      системных отказов
 * @param legitimate  правомерных отказов
 * @param byReason    по причинам
 * @param method      как посчитано
 */
public record RefusalsRs(Instant from, Instant to, long attempts, long denied, double deniedShare, long system, long legitimate,
                         List<ReasonRs> byReason, String method) {

    /**
     * Причина.
     *
     * @param reasonCode код причины
     * @param count      отказов
     * @param share      доля от отказов, 0..1
     * @param category   SYSTEM | LEGITIMATE
     */
    public record ReasonRs(String reasonCode, long count, double share, String category) {
    }
}
