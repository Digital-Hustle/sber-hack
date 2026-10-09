package ru.proxodnaya.access.api.dto.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Статистика проходов для assistant (FR-42, read-only /internal).
 *
 * @param from             начало периода
 * @param to               конец периода
 * @param totalAttempts    всего попыток
 * @param allow            пропущено
 * @param deny             отказов
 * @param denySystem       из них системных (качество кадра, лицо не найдено)
 * @param refusalsByCode   отказы по причинам
 * @param loadByHour       нагрузка по часам (входы/выходы)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessStatsRs(
        Instant from,
        Instant to,
        long totalAttempts,
        long allow,
        long deny,
        long denySystem,
        List<RefusalStat> refusalsByCode,
        List<HourLoad> loadByHour
) {
    public record RefusalStat(String code, long count, boolean system) {}
    public record HourLoad(int hour, long in, long out) {}
}
