package ru.proxodnaya.shift.features.analytics.query.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.analytics.query.model.HourlyLoad;
import ru.proxodnaya.shift.features.analytics.query.model.RefusalStats;
import ru.proxodnaya.shift.features.analytics.query.repository.PassStatsRepository;

/**
 * Статистика проходов: отказы по причинам (B-07) и нагрузка по часам (B-08).
 */
@Service
@Transactional(readOnly = true)
public class PassStatsQueryService {

    /** Отказы не по вине человека: плохой кадр и сбой системы (B-07). */
    public static final Set<String> SYSTEM_REASONS = Set.of("FACE_LOW_QUALITY", "SYSTEM_ERROR");

    private static final Duration MAX_PERIOD = Duration.ofDays(93);
    private static final Duration REFUSALS_DEFAULT = Duration.ofDays(7);
    private static final Duration LOAD_DEFAULT = Duration.ofDays(28);

    private final PassStatsRepository repository;
    private final ShiftProperties props;
    private final Clock clock;

    public PassStatsQueryService(PassStatsRepository repository, ShiftProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Отказы по причинам.
     *
     * @param from       начало или {@code null} — 7 дней до конца
     * @param until      конец, не включая, или {@code null} — сейчас
     * @param checkpoint проходная или {@code null}
     * @param simulated  включать демо-историю симулятора
     * @return статистика
     * @throws DomainException VALIDATION_ERROR — период пуст или длиннее 93 дней
     */
    public RefusalStats refusals(Instant from, Instant until, String checkpoint, boolean simulated) {
        Instant end = until == null ? clock.instant() : until;
        Instant start = from == null ? end.minus(REFUSALS_DEFAULT) : from;
        check(start, end);
        long attempts = repository.attempts(start, end, checkpoint, simulated);
        List<RefusalStats.ReasonCount> reasons = repository.deniedByReason(start, end, checkpoint, simulated).stream()
                .map(row -> new RefusalStats.ReasonCount(row.reasonCode(), row.count(), SYSTEM_REASONS.contains(row.reasonCode())))
                .toList();
        long denied = reasons.stream().mapToLong(RefusalStats.ReasonCount::count).sum();
        long system = reasons.stream().filter(RefusalStats.ReasonCount::system).mapToLong(RefusalStats.ReasonCount::count).sum();
        String method = "Решения access из проекции попыток (access.pass.v1), включая отказы до распознавания человека. Системные отказы — "
                + String.join(", ", SYSTEM_REASONS.stream().sorted().toList())
                + " (кадр или сбой, человек не виноват); остальные — правомерные (пропуск, допуск, смена, лицо не совпало). "
                + "Доля — от числа отказов; доля отказов — от всех попыток. " + AnalyticsQueryService.sourceNote(simulated);
        return new RefusalStats(start, end, attempts, denied, system, denied - system, reasons, method);
    }

    /**
     * Нагрузка по дню недели и часу.
     *
     * @param from       начало или {@code null} — 28 дней до конца
     * @param until      конец, не включая, или {@code null} — сейчас
     * @param checkpoint проходная или {@code null}
     * @param simulated  включать демо-историю симулятора
     * @return клетки
     * @throws DomainException VALIDATION_ERROR — период пуст или длиннее 93 дней
     */
    public HourlyLoad loadByHour(Instant from, Instant until, String checkpoint, boolean simulated) {
        Instant end = until == null ? clock.instant() : until;
        Instant start = from == null ? end.minus(LOAD_DEFAULT) : from;
        check(start, end);
        ZoneId zone = props.siteZone();
        String method = "Все попытки (пропуск, вручную, отказ) по дню недели и часу в поясе площадки (" + zone
                + "), 0 = понедельник. Пустые клетки не возвращаются. " + AnalyticsQueryService.sourceNote(simulated);
        return new HourlyLoad(start, end, zone, repository.byWeekdayHour(start, end, checkpoint, zone, simulated), method);
    }

    private static void check(Instant from, Instant until) {
        if (!from.isBefore(until) || Duration.between(from, until).compareTo(MAX_PERIOD) > 0) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Период — от 1 секунды до " + MAX_PERIOD.toDays() + " дней");
        }
    }
}
