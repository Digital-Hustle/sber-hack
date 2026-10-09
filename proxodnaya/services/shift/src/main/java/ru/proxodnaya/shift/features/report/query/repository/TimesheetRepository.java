package ru.proxodnaya.shift.features.report.query.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.report.query.model.PlannedShift;
import ru.proxodnaya.shift.features.report.query.model.PresenceSpan;
import ru.proxodnaya.shift.features.report.query.model.TimesheetPerson;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * Данные табеля: интервалы, смены и карточки рабочих. Симулятор исключён.
 */
@Repository
public class TimesheetRepository {

    private static final ru.proxodnaya.shift.jooq.tables.PresenceIntervals INTERVALS = Tables.PRESENCE_INTERVALS;
    private static final ru.proxodnaya.shift.jooq.tables.Shifts SHIFTS = Tables.SHIFTS;
    private static final ru.proxodnaya.shift.jooq.tables.Workers WORKERS = Tables.WORKERS;

    private final DSLContext dsl;

    public TimesheetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Интервалы, пересекающие {@code [from, until)}: свежий открытый — до {@code now}, устаревший — 0 минут и неполный.
     *
     * @param from       начало
     * @param until      конец
     * @param now        текущий момент
     * @param freshSince граница свежести открытого интервала
     * @return интервалы
     */
    public List<PresenceSpan> spans(Instant from, Instant until, Instant now, Instant freshSince) {
        Condition fresh = INTERVALS.ENTERED_AT.ge(utc(freshSince));
        Field<OffsetDateTime> end = DSL.coalesce(INTERVALS.EXITED_AT,
                DSL.when(fresh, DSL.val(utc(now))).otherwise(INTERVALS.ENTERED_AT));
        Field<Boolean> incomplete = DSL.field(DSL.condition("'UNCONFIRMED_EXIT' = any({0})", INTERVALS.FLAGS)
                .or(INTERVALS.EXITED_AT.isNull().and(fresh.not())));
        return dsl.select(INTERVALS.WORKER_ID, INTERVALS.ENTERED_AT, end, incomplete)
                .from(INTERVALS)
                .where(INTERVALS.SIMULATED.isFalse())
                .and(INTERVALS.ENTERED_AT.lt(utc(until)))
                .and(INTERVALS.EXITED_AT.isNull().or(INTERVALS.EXITED_AT.ge(utc(from))))
                .orderBy(INTERVALS.WORKER_ID, INTERVALS.ENTERED_AT)
                .fetch(row -> new PresenceSpan(row.value1(), row.value2().toInstant(), row.value3().toInstant(),
                        Boolean.TRUE.equals(row.value4())));
    }

    /**
     * Смены, начинающиеся в {@code [from, until)}.
     *
     * @param from  начало
     * @param until конец
     * @return смены
     */
    public List<PlannedShift> shifts(Instant from, Instant until) {
        return dsl.select(SHIFTS.WORKER_ID, SHIFTS.STARTS_AT)
                .from(SHIFTS)
                .where(SHIFTS.STARTS_AT.ge(utc(from)))
                .and(SHIFTS.STARTS_AT.lt(utc(until)))
                .fetch(row -> new PlannedShift(row.value1(), row.value2().toInstant()));
    }

    /**
     * Карточки рабочих по id; кого нет в проекции — не вернётся.
     *
     * @param workerIds рабочие
     * @return карточки
     */
    public List<TimesheetPerson> people(Collection<String> workerIds) {
        return dsl.select(WORKERS.ID, WORKERS.FULL_NAME, WORKERS.POSITION, WORKERS.CONTRACTOR)
                .from(WORKERS)
                .where(WORKERS.ID.in(workerIds))
                .fetch(row -> new TimesheetPerson(row.value1(), row.value2(), row.value3(), row.value4()));
    }

    private static OffsetDateTime utc(Instant moment) {
        return moment.atOffset(ZoneOffset.UTC);
    }
}
