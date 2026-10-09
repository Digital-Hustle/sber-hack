package ru.proxodnaya.access.features.pass.query.model;

import java.time.Instant;
import java.util.List;

/**
 * Статистика для assistant (/internal/access/stats, FR-42).
 */
public record AccessStats(
        Instant from, Instant to,
        long totalAttempts, long allow, long deny, long denySystem,
        List<RefusalStat> refusalsByCode,
        List<HourLoad> loadByHour
) {
    public record RefusalStat(String code, long count, boolean system) {}
    public record HourLoad(int hour, long in, long out) {}
}
