package ru.proxodnaya.shift.features.analytics.query.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.analytics.query.model.Summary;
import ru.proxodnaya.shift.features.analytics.query.model.WorkerHours;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * SQL аналитики по реальным интервалам и проекции попыток. Симулятор исключён.
 */
@Repository
public class AnalyticsRepository {

    private static final ru.proxodnaya.shift.jooq.tables.PresenceIntervals INTERVALS = Tables.PRESENCE_INTERVALS;
    private static final ru.proxodnaya.shift.jooq.tables.PassEvents PASS_EVENTS = Tables.PASS_EVENTS;
    private static final String SIMULATOR = "SIMULATOR";
    private static final long SECONDS_IN_MINUTE = 60;

    private final DSLContext dsl;

    public AnalyticsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сколько человек сейчас в каждой зоне: открытые интервалы со входом не раньше {@code freshSince}.
     *
     * @param freshSince граница свежести
     * @return по зонам, больше — выше
     */
    public List<Summary.ZoneCount> insideByZone(Instant freshSince) {
        Field<Integer> count = DSL.count();
        return dsl.select(INTERVALS.ZONE_ID, count)
                .from(INTERVALS)
                .where(INTERVALS.EXITED_AT.isNull())
                .and(INTERVALS.SIMULATED.isFalse())
                .and(INTERVALS.ENTERED_AT.ge(utc(freshSince)))
                .groupBy(INTERVALS.ZONE_ID)
                .orderBy(count.desc(), INTERVALS.ZONE_ID)
                .fetch(row -> new Summary.ZoneCount(row.value1(), row.value2()));
    }

    /**
     * Сколько открытых интервалов старше {@code freshSince} (выход не отмечен).
     *
     * @param freshSince граница свежести
     * @return число
     */
    public long staleOpen(Instant freshSince) {
        return dsl.fetchCount(INTERVALS, INTERVALS.EXITED_AT.isNull()
                .and(INTERVALS.SIMULATED.isFalse())
                .and(INTERVALS.ENTERED_AT.lt(utc(freshSince))));
    }

    /**
     * Проходы в {@code [from, until)} по решению.
     *
     * @param from  начало
     * @param until конец
     * @return пропущено, вручную, отказов
     */
    public Summary.PassCounts passes(Instant from, Instant until) {
        Condition period = PASS_EVENTS.TS.ge(utc(from)).and(PASS_EVENTS.TS.lt(utc(until))).and(PASS_EVENTS.SOURCE.ne(SIMULATOR));
        return dsl.select(
                        DSL.count().filterWhere(PASS_EVENTS.DECISION.eq("ALLOW")),
                        DSL.count().filterWhere(PASS_EVENTS.DECISION.eq("MANUAL")),
                        DSL.count().filterWhere(PASS_EVENTS.DECISION.eq("DENY")))
                .from(PASS_EVENTS)
                .where(period)
                .fetchOne(row -> new Summary.PassCounts(row.value1(), row.value2(), row.value3()));
    }

    /**
     * Отработанное время по рабочим: пересечение интервалов с {@code [from, until)}; открытые свежие — до {@code now},
     * открытые старше {@code freshSince} — 0 минут и в {@code incomplete}.
     *
     * @param from       начало
     * @param until      конец
     * @param now        текущий момент
     * @param freshSince граница свежести открытого интервала
     * @param workerId   рабочий или {@code null}
     * @param zoneId     зона или {@code null}
     * @return по рабочим
     */
    public List<WorkerHours> hours(Instant from, Instant until, Instant now, Instant freshSince, String workerId, String zoneId) {
        Condition fresh = INTERVALS.ENTERED_AT.ge(utc(freshSince));
        Field<OffsetDateTime> end = DSL.coalesce(INTERVALS.EXITED_AT,
                DSL.when(fresh, DSL.val(utc(now))).otherwise(INTERVALS.ENTERED_AT));
        Field<Double> seconds = DSL.field("extract(epoch from (least({0}, {1}) - greatest({2}, {3})))", SQLDataType.DOUBLE,
                end, DSL.val(utc(until)), INTERVALS.ENTERED_AT, DSL.val(utc(from)));
        Field<BigDecimal> total = DSL.coalesce(DSL.sum(DSL.greatest(seconds, DSL.inline(0d))), BigDecimal.ZERO);
        Condition incomplete = DSL.condition("'UNCONFIRMED_EXIT' = any({0})", INTERVALS.FLAGS)
                .or(INTERVALS.EXITED_AT.isNull().and(fresh.not()));
        Condition where = INTERVALS.SIMULATED.isFalse()
                .and(INTERVALS.ENTERED_AT.lt(utc(until)))
                .and(INTERVALS.EXITED_AT.isNull().or(INTERVALS.EXITED_AT.gt(utc(from))));
        if (workerId != null) {
            where = where.and(INTERVALS.WORKER_ID.eq(workerId));
        }
        if (zoneId != null) {
            where = where.and(INTERVALS.ZONE_ID.eq(zoneId));
        }
        return dsl.select(INTERVALS.WORKER_ID, total, DSL.count(), DSL.count().filterWhere(incomplete))
                .from(INTERVALS)
                .where(where)
                .groupBy(INTERVALS.WORKER_ID)
                .orderBy(total.desc(), INTERVALS.WORKER_ID)
                .fetch(row -> new WorkerHours(row.value1(), row.value2().longValue() / SECONDS_IN_MINUTE, row.value3(), row.value4()));
    }

    private static OffsetDateTime utc(Instant moment) {
        return moment.atOffset(ZoneOffset.UTC);
    }
}
