package ru.proxodnaya.shift.features.analytics.query.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.shift.config.properties.AnalyticsProperties;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.analytics.query.model.HoursReport;
import ru.proxodnaya.shift.features.analytics.query.model.LateOvertimeReport;
import ru.proxodnaya.shift.features.analytics.query.model.PresenceFact;
import ru.proxodnaya.shift.features.analytics.query.model.ShiftOutcome;
import ru.proxodnaya.shift.features.analytics.query.model.ShiftSlot;
import ru.proxodnaya.shift.features.analytics.query.model.Summary;
import ru.proxodnaya.shift.features.analytics.query.model.WorkerHours;
import ru.proxodnaya.shift.features.analytics.query.model.WorkerLateOvertime;
import ru.proxodnaya.shift.features.analytics.query.repository.AnalyticsRepository;

/**
 * Аналитика (FR-24/30/32/33): каждая цифра — SQL по интервалам и попыткам, с объяснением в {@code method}.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsQueryService {

    static final Duration MAX_PERIOD = Duration.ofDays(93);
    private static final Duration MAX_SHIFT = Duration.ofHours(24);
    private static final String INCLUDING_SIMULATOR = "Включая демо-историю симулятора (source = SIMULATOR): это не реальные проходы.";

    private final AnalyticsRepository repository;
    private final ShiftProperties props;
    private final Clock clock;
    private final LateOvertimeCalculator calculator;
    private final AnalyticsProperties thresholds;

    public AnalyticsQueryService(AnalyticsRepository repository, ShiftProperties props, AnalyticsProperties thresholds, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
        this.thresholds = thresholds;
        this.calculator = new LateOvertimeCalculator(props.earlyEntry(), thresholds.lateGrace(), thresholds.overtimeThreshold());
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
        String method = "На объекте — открытые интервалы присутствия (вход без выхода) моложе " + wholeHours(props.presenceTtl())
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
     * @param zoneId    зона или {@code null}
     * @param simulated включать демо-историю симулятора
     * @return отчёт
     * @throws DomainException VALIDATION_ERROR — период пуст или длиннее 93 дней
     */
    public HoursReport hours(Instant from, Instant until, String workerId, String zoneId, boolean simulated) {
        checkPeriod(from, until);
        Instant now = clock.instant();
        List<WorkerHours> items = repository.hours(from, until, now, now.minus(props.presenceTtl()), workerId, zoneId,
                simulated);
        String method = "Сумма интервалов присутствия, обрезанных границами периода. Незакрытый интервал моложе "
                + wholeHours(props.presenceTtl()) + " ч считается до текущего момента; старше — 0 минут и в incomplete. "
                + "Автозакрытые по концу смены (выход не отмечен) считаются до конца смены и тоже в incomplete. "
                + sourceNote(simulated);
        return new HoursReport(from, until, items, method);
    }

    /**
     * Опоздания и переработки по сменам, начавшимся в периоде (B-03).
     *
     * @param from     начало
     * @param until    конец, не включая
     * @param workerId  рабочий или {@code null}; с рабочим в ответе — разбор по сменам
     * @param simulated включать демо-историю симулятора
     * @return отчёт
     * @throws DomainException VALIDATION_ERROR — период пуст или длиннее 93 дней
     */
    public LateOvertimeReport lateOvertime(Instant from, Instant until, String workerId, boolean simulated) {
        checkPeriod(from, until);
        Instant now = clock.instant();
        List<ShiftSlot> shifts = repository.startedShifts(from, until, now, workerId);
        List<ShiftOutcome> outcomes = new ArrayList<>();
        if (!shifts.isEmpty()) {
            Map<String, List<PresenceFact>> facts = repository.presence(
                            shifts.stream().map(ShiftSlot::workerId).distinct().toList(), from.minus(props.earlyEntry()),
                            until.plus(MAX_SHIFT), now, now.minus(props.presenceTtl()), simulated)
                    .stream().collect(Collectors.groupingBy(PresenceFact::workerId));
            for (ShiftSlot shift : shifts) {
                outcomes.add(calculator.evaluate(shift, facts.getOrDefault(shift.workerId(), List.of()), now));
            }
        }
        String method = "Смены, начавшиеся в периоде (уже начавшиеся). Опоздание — первый вход в окне смены (от "
                + props.earlyEntry().toMinutes() + " мин до начала до конца смены) позже начала больше чем на "
                + thresholds.lateGrace().toMinutes() + " мин. Неявка — смена закончилась, входа не было. Переработка — последний выход "
                + "позже конца смены не меньше чем на " + thresholds.overtimeThreshold().toMinutes() + " мин; если выход не отмечен "
                + "(автозакрытие, повторный вход, вход без выхода дольше " + wholeHours(props.presenceTtl())
                + " ч) — переработка не считается, смена в incomplete. Если человек ещё на объекте — до текущего момента. "
                + sourceNote(simulated);
        return new LateOvertimeReport(from, until, summarize(outcomes), workerId == null ? List.of() : List.copyOf(outcomes), method);
    }

    private static List<WorkerLateOvertime> summarize(List<ShiftOutcome> outcomes) {
        Map<String, List<ShiftOutcome>> byWorker = outcomes.stream()
                .collect(Collectors.groupingBy(ShiftOutcome::workerId, LinkedHashMap::new, Collectors.toList()));
        List<WorkerLateOvertime> items = new ArrayList<>();
        byWorker.forEach((worker, list) -> items.add(new WorkerLateOvertime(worker, list.size(),
                list.stream().filter(item -> item.arrival() == ShiftOutcome.Arrival.LATE).count(),
                list.stream().mapToLong(ShiftOutcome::lateMinutes).sum(),
                list.stream().filter(item -> item.arrival() == ShiftOutcome.Arrival.ABSENT).count(),
                list.stream().filter(ShiftOutcome::overtime).count(),
                list.stream().filter(ShiftOutcome::overtime).mapToLong(ShiftOutcome::overtimeMinutes).sum(),
                list.stream().filter(ShiftOutcome::incomplete).count())));
        items.sort(Comparator.comparingLong(WorkerLateOvertime::late).reversed()
                .thenComparing(Comparator.comparingLong(WorkerLateOvertime::lateMinutes).reversed())
                .thenComparing(WorkerLateOvertime::workerId));
        return items;
    }

    private static void checkPeriod(Instant from, Instant until) {
        if (!from.isBefore(until) || Duration.between(from, until).compareTo(MAX_PERIOD) > 0) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Период — от 1 секунды до " + MAX_PERIOD.toDays() + " дней");
        }
    }

    /**
     * Пояснение для {@code method}: учтена ли демо-история симулятора (FR-34, ADR-007).
     *
     * @param simulated учтена ли
     * @return фраза
     */
    static String sourceNote(boolean simulated) {
        return simulated ? INCLUDING_SIMULATOR : "Симулятор исключён.";
    }

    private static long wholeHours(Duration duration) {
        return duration.toHours();
    }
}
