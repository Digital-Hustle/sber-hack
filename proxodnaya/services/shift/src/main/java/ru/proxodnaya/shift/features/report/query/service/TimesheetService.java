package ru.proxodnaya.shift.features.report.query.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.report.query.model.DayMark;
import ru.proxodnaya.shift.features.report.query.model.PlannedShift;
import ru.proxodnaya.shift.features.report.query.model.PresenceSpan;
import ru.proxodnaya.shift.features.report.query.model.Timesheet;
import ru.proxodnaya.shift.features.report.query.model.TimesheetDay;
import ru.proxodnaya.shift.features.report.query.model.TimesheetPerson;
import ru.proxodnaya.shift.features.report.query.model.TimesheetRow;
import ru.proxodnaya.shift.features.report.query.repository.TimesheetRepository;

/**
 * Табель (B-06): строки — люди, столбцы — дни площадки, в клетке код Т/Н и часы по реальным интервалам.
 */
@Service
@Transactional(readOnly = true)
public class TimesheetService {

    static final int MAX_DAYS = 62;
    private static final long SECONDS_IN_MINUTE = 60;

    private final TimesheetRepository repository;
    private final ShiftProperties props;
    private final Clock clock;

    public TimesheetService(TimesheetRepository repository, ShiftProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Собирает табель.
     *
     * @param from       первый день
     * @param last       последний день, включительно
     * @param contractor подрядчик (без учёта регистра) или {@code null} — все
     * @return табель
     * @throws DomainException VALIDATION_ERROR — конец раньше начала или период длиннее 62 дней
     */
    public Timesheet build(LocalDate from, LocalDate last, String contractor) {
        long length = ChronoUnit.DAYS.between(from, last) + 1;
        if (length < 1 || length > MAX_DAYS) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Период — от 1 до " + MAX_DAYS + " дней, конец не раньше начала");
        }
        ZoneId zone = props.siteZone();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant until = last.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = clock.instant();
        List<PresenceSpan> spans = repository.spans(start, until, now, now.minus(props.presenceTtl()));
        List<PlannedShift> shifts = repository.shifts(start, until);
        List<LocalDate> days = from.datesUntil(last.plusDays(1)).toList();
        String filter = contractor == null || contractor.isBlank() ? null : contractor.strip();
        List<TimesheetRow> rows = new ArrayList<>();
        for (TimesheetPerson person : people(spans, shifts, filter)) {
            rows.add(row(person, days, spans, shifts, now.atZone(zone).toLocalDate()));
        }
        rows.sort(Comparator.comparing((TimesheetRow row) -> row.person().contractor(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(row -> row.person().fullName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> row.person().workerId()));
        return new Timesheet(from, last, filter, zone, days, rows, now, method());
    }

    private List<TimesheetPerson> people(List<PresenceSpan> spans, List<PlannedShift> shifts, String contractor) {
        SortedSet<String> ids = new TreeSet<>();
        spans.forEach(span -> ids.add(span.workerId()));
        shifts.forEach(shift -> ids.add(shift.workerId()));
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, TimesheetPerson> known = repository.people(ids).stream()
                .collect(Collectors.toMap(TimesheetPerson::workerId, Function.identity()));
        List<TimesheetPerson> result = new ArrayList<>();
        for (String id : ids) {
            TimesheetPerson card = known.get(id);
            TimesheetPerson person = card == null || card.fullName() == null
                    ? new TimesheetPerson(id, id, null, card == null ? null : card.contractor())
                    : card;
            if (contractor == null || person.contractor() != null
                    && person.contractor().toLowerCase(Locale.ROOT).equals(contractor.toLowerCase(Locale.ROOT))) {
                result.add(person);
            }
        }
        return result;
    }

    private TimesheetRow row(TimesheetPerson person, List<LocalDate> days, List<PresenceSpan> spans, List<PlannedShift> shifts,
                             LocalDate today) {
        Days grid = new Days(days.getFirst(), days.size(), props.siteZone());
        spans.stream().filter(span -> span.workerId().equals(person.workerId())).forEach(grid::spread);
        shifts.stream().filter(shift -> shift.workerId().equals(person.workerId())).forEach(grid::plan);
        List<TimesheetDay> cells = new ArrayList<>(days.size());
        for (int day = 0; day < days.size(); day++) {
            cells.add(grid.cell(day, days.get(day).isBefore(today)));
        }
        int worked = (int) cells.stream().filter(cell -> cell.mark() == DayMark.PRESENT).count();
        int absences = (int) cells.stream().filter(cell -> cell.mark() == DayMark.ABSENT).count();
        int partial = (int) cells.stream().filter(TimesheetDay::incomplete).count();
        long minutes = cells.stream().mapToLong(TimesheetDay::minutes).sum();
        return new TimesheetRow(person, List.copyOf(cells), worked, minutes, absences, partial);
    }

    private String method() {
        return "Дни — в часовом поясе площадки (" + props.siteZone() + "). Часы — сумма интервалов присутствия (вход → выход), "
                + "разрезанных по границам суток. Т — был на объекте хотя бы раз за день; Н — плановая смена началась в этот день, "
                + "день прошёл, а прохода не было. Выход не отмечен (автозакрытие по концу смены или вход старше "
                + props.presenceTtl().toHours() + " ч без выхода) — день помечен неполным: часы до конца смены или 0. "
                + "Свежий открытый интервал считается до момента выгрузки. Симулятор исключён.";
    }

    /**
     * Сетка «дни × секунды» одного человека в поясе площадки.
     */
    private static final class Days {
        private final LocalDate first;
        private final int size;
        private final ZoneId zone;
        private final long[] seconds;
        private final boolean[] present;
        private final boolean[] incomplete;
        private final boolean[] planned;

        Days(LocalDate first, int size, ZoneId zone) {
            this.first = first;
            this.size = size;
            this.zone = zone;
            this.seconds = new long[size];
            this.present = new boolean[size];
            this.incomplete = new boolean[size];
            this.planned = new boolean[size];
        }

        void plan(PlannedShift shift) {
            int day = index(shift.start());
            if (day >= 0 && day < size) {
                planned[day] = true;
            }
        }

        void spread(PresenceSpan span) {
            Instant start = max(span.start(), dayStart(0));
            Instant end = min(span.end(), dayStart(size));
            int day = index(start);
            if (day < 0 || day >= size) {
                return;
            }
            mark(day, span.incomplete());
            for (; day < size && dayStart(day).isBefore(end); day++) {
                Instant from = max(start, dayStart(day));
                Instant until = min(end, dayStart(day + 1));
                if (until.isAfter(from)) {
                    seconds[day] += Duration.between(from, until).toSeconds();
                    mark(day, span.incomplete());
                }
            }
        }

        TimesheetDay cell(int day, boolean past) {
            DayMark mark = DayMark.NONE;
            if (present[day]) {
                mark = DayMark.PRESENT;
            } else if (planned[day] && past) {
                mark = DayMark.ABSENT;
            }
            return new TimesheetDay(mark, seconds[day] / SECONDS_IN_MINUTE, incomplete[day]);
        }

        private void mark(int day, boolean partial) {
            present[day] = true;
            incomplete[day] |= partial;
        }

        private int index(Instant moment) {
            return (int) ChronoUnit.DAYS.between(first, moment.atZone(zone).toLocalDate());
        }

        private Instant dayStart(int day) {
            return first.plusDays(day).atStartOfDay(zone).toInstant();
        }

        private static Instant max(Instant left, Instant right) {
            return left.isAfter(right) ? left : right;
        }

        private static Instant min(Instant left, Instant right) {
            return left.isBefore(right) ? left : right;
        }
    }
}
