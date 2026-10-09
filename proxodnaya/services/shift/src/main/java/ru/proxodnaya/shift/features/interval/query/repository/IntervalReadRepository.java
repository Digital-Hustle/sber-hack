package ru.proxodnaya.shift.features.interval.query.repository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.interval.model.CloseReason;
import ru.proxodnaya.shift.features.interval.model.Interval;
import ru.proxodnaya.shift.features.interval.model.IntervalFilter;
import ru.proxodnaya.shift.features.interval.model.IntervalFlag;
import ru.proxodnaya.shift.jooq.Tables;
import ru.proxodnaya.shift.jooq.tables.records.PresenceIntervalsRecord;
import ru.proxodnaya.shift.shared.PageCursor;

/**
 * Чтение интервалов присутствия.
 */
@Repository
public class IntervalReadRepository {

    private static final ru.proxodnaya.shift.jooq.tables.PresenceIntervals INTERVALS = Tables.PRESENCE_INTERVALS;
    private static final double MILLIS = 1000d;

    private final DSLContext dsl;

    public IntervalReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Страница интервалов, новые сверху.
     *
     * @param filter фильтр
     * @param after  курсор или {@code null}
     * @param limit  сколько строк взять
     * @return интервалы
     */
    public List<Interval> page(IntervalFilter filter, PageCursor after, int limit) {
        Condition where = where(filter);
        if (after != null) {
            where = where.and(DSL.row(INTERVALS.ENTERED_AT, INTERVALS.ID).lt(utc(after.ts()), after.id()));
        }
        return dsl.selectFrom(INTERVALS).where(where)
                .orderBy(INTERVALS.ENTERED_AT.desc(), INTERVALS.ID.desc())
                .limit(limit)
                .fetch(IntervalReadRepository::interval);
    }

    /**
     * Сколько интервалов по фильтру.
     *
     * @param filter фильтр
     * @return число
     */
    public long count(IntervalFilter filter) {
        return dsl.fetchCount(INTERVALS, where(filter));
    }

    /**
     * Открытый интервал рабочего со входом не раньше {@code since}.
     *
     * @param workerId рабочий
     * @param since    граница свежести
     * @return интервал
     */
    public Optional<Interval> openSince(String workerId, Instant since) {
        return dsl.selectFrom(INTERVALS)
                .where(INTERVALS.WORKER_ID.eq(workerId))
                .and(INTERVALS.EXITED_AT.isNull())
                .and(INTERVALS.ENTERED_AT.ge(utc(since)))
                .fetchOptional(IntervalReadRepository::interval);
    }

    /**
     * Сумма пересечений интервалов рабочего с {@code [from, until)}: закрытые — до выхода, открытые со входом не раньше
     * {@code freshSince} — до {@code now}, остальные открытые не считаются.
     *
     * @param workerId   рабочий
     * @param from       начало
     * @param until      конец
     * @param now        текущий момент
     * @param freshSince граница свежести открытого интервала
     * @return длительность
     */
    public Duration worked(String workerId, Instant from, Instant until, Instant now, Instant freshSince) {
        Field<OffsetDateTime> end = DSL.coalesce(INTERVALS.EXITED_AT,
                DSL.when(INTERVALS.ENTERED_AT.ge(utc(freshSince)), DSL.val(utc(now))).otherwise(INTERVALS.ENTERED_AT));
        Field<OffsetDateTime> clippedStart = DSL.greatest(INTERVALS.ENTERED_AT, DSL.val(utc(from)));
        Field<OffsetDateTime> clippedEnd = DSL.least(end, DSL.val(utc(until)));
        Field<Double> seconds = DSL.field("extract(epoch from ({0} - {1}))", SQLDataType.DOUBLE, clippedEnd, clippedStart);
        BigDecimal total = dsl.select(DSL.coalesce(DSL.sum(DSL.greatest(seconds, DSL.inline(0d))), BigDecimal.ZERO))
                .from(INTERVALS)
                .where(INTERVALS.WORKER_ID.eq(workerId))
                .and(INTERVALS.SIMULATED.isFalse())
                .and(INTERVALS.ENTERED_AT.lt(utc(until)))
                .and(INTERVALS.EXITED_AT.isNull().or(INTERVALS.EXITED_AT.gt(utc(from))))
                .fetchOne(0, BigDecimal.class);
        return Duration.ofMillis(Math.round((total == null ? 0 : total.doubleValue()) * MILLIS));
    }

    private static Condition where(IntervalFilter filter) {
        List<Condition> conditions = new ArrayList<>();
        if (filter.period().from() != null) {
            conditions.add(INTERVALS.ENTERED_AT.ge(utc(filter.period().from())));
        }
        if (filter.period().until() != null) {
            conditions.add(INTERVALS.ENTERED_AT.lt(utc(filter.period().until())));
        }
        if (filter.workerId() != null) {
            conditions.add(INTERVALS.WORKER_ID.eq(filter.workerId()));
        }
        if (filter.zoneId() != null) {
            conditions.add(INTERVALS.ZONE_ID.eq(filter.zoneId()));
        }
        if (!filter.includeSimulated()) {
            conditions.add(INTERVALS.SIMULATED.isFalse());
        }
        return DSL.and(conditions);
    }

    private static Interval interval(PresenceIntervalsRecord row) {
        return new Interval(row.getId(), row.getWorkerId(), row.getZoneId(), row.getShiftId(), row.getEnteredAt().toInstant(),
                row.getExitedAt() == null ? null : row.getExitedAt().toInstant(),
                row.getCloseReason() == null ? null : CloseReason.valueOf(row.getCloseReason()),
                Arrays.stream(row.getFlags()).map(IntervalFlag::valueOf).toList(), row.getSimulated());
    }

    private static OffsetDateTime utc(Instant moment) {
        return moment.atOffset(ZoneOffset.UTC);
    }
}
