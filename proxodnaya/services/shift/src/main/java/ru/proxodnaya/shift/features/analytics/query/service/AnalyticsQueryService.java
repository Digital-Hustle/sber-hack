package ru.proxodnaya.shift.features.analytics.query.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.analytics.query.model.HoursReport;
import ru.proxodnaya.shift.features.analytics.query.model.Summary;
import ru.proxodnaya.shift.features.analytics.query.model.WorkerHours;
import ru.proxodnaya.shift.features.analytics.query.repository.AnalyticsRepository;

/**
 * Аналитика (FR-24/30/32/33): каждая цифра — SQL по интервалам и попыткам, с объяснением в {@code method}.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsQueryService {

    static final Duration MAX_PERIOD = Duration.ofDays(93);

    private final AnalyticsRepository repository;
    private final ShiftProperties props;
    private final Clock clock;

    public AnalyticsQueryService(AnalyticsRepository repository, ShiftProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Кто на объекте сейчас и проходы за сегодня.
     *
     * @return сводка
     */
    public Summary summary() {
        Instant now = clock.instant();
        Instant fresh = now.minus(props.presenceTtl());
        List<Summary.ZoneCount> zones = repository.insideByZone(fresh);
        long inside = zones.stream().mapToLong(Summary.ZoneCount::inside).sum();
        ZoneId zone = props.siteZone();
        LocalDate today = now.atZone(zone).toLocalDate();
        Summary.PassCounts passes = repository.passes(today.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant());
        String method = "На объекте — открытые интервалы присутствия (вход без выхода) моложе " + hours(props.presenceTtl())
                + " ч; интервалы старше — в incomplete, в «на объекте» не входят. Проходы — решения access за сегодня (" + zone
                + "). Симулятор исключён.";
        return new Summary(now, inside, zones, repository.staleOpen(fresh), passes, method);
    }

    /**
     * Отработанные часы по рабочим за период.
     *
     * @param from     начало
     * @param until    конец, не включая
     * @param workerId рабочий или {@code null}
     * @param zoneId   зона или {@code null}
     * @return отчёт
     * @throws DomainException VALIDATION_ERROR — период пуст или длиннее 93 дней
     */
    public HoursReport hours(Instant from, Instant until, String workerId, String zoneId) {
        if (!from.isBefore(until) || Duration.between(from, until).compareTo(MAX_PERIOD) > 0) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Период — от 1 секунды до " + MAX_PERIOD.toDays() + " дней");
        }
        Instant now = clock.instant();
        List<WorkerHours> items = repository.hours(from, until, now, now.minus(props.presenceTtl()), workerId, zoneId);
        String method = "Сумма интервалов присутствия, обрезанных границами периода. Незакрытый интервал моложе "
                + hours(props.presenceTtl()) + " ч считается до текущего момента; старше — 0 минут и в incomplete. "
                + "Автозакрытые по концу смены (выход не отмечен) считаются до конца смены и тоже в incomplete. Симулятор исключён.";
        return new HoursReport(from, until, items, method);
    }

    private static long hours(Duration duration) {
        return duration.toHours();
    }
}
