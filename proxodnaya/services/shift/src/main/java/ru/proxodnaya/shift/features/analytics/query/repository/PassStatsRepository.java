package ru.proxodnaya.shift.features.analytics.query.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.analytics.query.model.HourlyLoad;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * SQL статистики проходов по проекции попыток {@code pass_events}. Симулятор исключён.
 */
@Repository
public class PassStatsRepository {

    private static final ru.proxodnaya.shift.jooq.tables.PassEvents PASS_EVENTS = Tables.PASS_EVENTS;
    private static final String DENY = "DENY";

    private final DSLContext dsl;

    public PassStatsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Всего попыток.
     *
     * @param from       начало
     * @param until      конец
     * @param checkpoint проходная или {@code null}
     * @return число
     */
    public long attempts(Instant from, Instant until, String checkpoint) {
        return dsl.fetchCount(PASS_EVENTS, where(from, until, checkpoint));
    }

    /**
     * Отказы по причинам, больше — выше.
     *
     * @param from       начало
     * @param until      конец
     * @param checkpoint проходная или {@code null}
     * @return пары «код — число»
     */
    public List<ReasonRow> deniedByReason(Instant from, Instant until, String checkpoint) {
        Field<Integer> count = DSL.count();
        return dsl.select(PASS_EVENTS.REASON_CODE, count)
                .from(PASS_EVENTS)
                .where(where(from, until, checkpoint).and(PASS_EVENTS.DECISION.eq(DENY)))
                .groupBy(PASS_EVENTS.REASON_CODE)
                .orderBy(count.desc(), PASS_EVENTS.REASON_CODE)
                .fetch(row -> new ReasonRow(row.value1(), row.value2()));
    }

    /**
     * Попытки по дню недели и часу в поясе площадки.
     *
     * @param from       начало
     * @param until      конец
     * @param checkpoint проходная или {@code null}
     * @param zone       пояс площадки
     * @return непустые клетки по дню и часу
     */
    public List<HourlyLoad.Cell> byWeekdayHour(Instant from, Instant until, String checkpoint, ZoneId zone) {
        // пояс — литералом: с bind-параметром Postgres не сочтёт выражения в select и group by одинаковыми
        Field<OffsetDateTime> local = PASS_EVENTS.TS;
        Field<Integer> weekday = DSL.field("(extract(isodow from {0} at time zone {1})::int - 1)", SQLDataType.INTEGER,
                local, DSL.inline(zone.getId()));
        Field<Integer> hour = DSL.field("extract(hour from {0} at time zone {1})::int", SQLDataType.INTEGER, local,
                DSL.inline(zone.getId()));
        Field<Integer> denied = DSL.count().filterWhere(PASS_EVENTS.DECISION.eq(DENY));
        return dsl.select(weekday, hour, DSL.count(), denied)
                .from(PASS_EVENTS)
                .where(where(from, until, checkpoint))
                .groupBy(weekday, hour)
                .orderBy(weekday, hour)
                .fetch(row -> new HourlyLoad.Cell(row.value1(), row.value2(), row.value3(), row.value3() - row.value4(), row.value4()));
    }

    private static Condition where(Instant from, Instant until, String checkpoint) {
        Condition where = PASS_EVENTS.TS.ge(utc(from)).and(PASS_EVENTS.TS.lt(utc(until))).and(PASS_EVENTS.SOURCE.ne("SIMULATOR"));
        return checkpoint == null ? where : where.and(PASS_EVENTS.CHECKPOINT_ID.eq(checkpoint));
    }

    private static OffsetDateTime utc(Instant moment) {
        return moment.atOffset(ZoneOffset.UTC);
    }

    /**
     * Строка «причина — число».
     *
     * @param reasonCode код
     * @param count      число
     */
    public record ReasonRow(String reasonCode, long count) {
    }
}
