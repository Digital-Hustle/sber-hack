package ru.proxodnaya.shift.features.shift.query.repository;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DatePart;
import org.jooq.Record5;
import org.jooq.SelectSelectStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.shift.model.Shift;
import ru.proxodnaya.shift.features.shift.model.ShiftFilter;
import ru.proxodnaya.shift.features.shift.model.ShiftTemplate;
import ru.proxodnaya.shift.jooq.Tables;
import ru.proxodnaya.shift.shared.PageCursor;

/**
 * Чтение смен и шаблонов.
 */
@Repository
public class ShiftReadRepository {

    private static final ru.proxodnaya.shift.jooq.tables.Shifts SHIFTS = Tables.SHIFTS;
    private static final ru.proxodnaya.shift.jooq.tables.ShiftTemplates TEMPLATES = Tables.SHIFT_TEMPLATES;

    private final DSLContext dsl;

    public ShiftReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Страница смен по началу, ранние сверху (расписание читают вперёд).
     *
     * @param filter фильтр
     * @param after  курсор или {@code null}
     * @param limit  сколько строк взять
     * @return смены
     */
    public List<Shift> page(ShiftFilter filter, PageCursor after, int limit) {
        Condition where = where(filter);
        if (after != null) {
            where = where.and(DSL.row(SHIFTS.STARTS_AT, SHIFTS.ID).gt(utc(after.ts()), after.id()));
        }
        return columns().from(SHIFTS).where(where).orderBy(SHIFTS.STARTS_AT, SHIFTS.ID).limit(limit).fetch(ShiftReadRepository::shift);
    }

    /**
     * Сколько смен по фильтру.
     *
     * @param filter фильтр
     * @return число
     */
    public long count(ShiftFilter filter) {
        return dsl.fetchCount(SHIFTS, where(filter));
    }

    /**
     * Смена, в окно которой попадает момент: {@code start − early ≤ moment < end}; при нескольких — ранняя.
     *
     * @param workerId рабочий
     * @param moment   момент
     * @param early    допуск раннего входа
     * @return смена
     */
    public Optional<Shift> covering(String workerId, Instant moment, Duration early) {
        return columns().from(SHIFTS)
                .where(SHIFTS.WORKER_ID.eq(workerId))
                .and(SHIFTS.STARTS_AT.le(utc(moment.plus(early))))
                .and(SHIFTS.ENDS_AT.gt(utc(moment)))
                .orderBy(SHIFTS.STARTS_AT)
                .limit(1)
                .fetchOptional(ShiftReadRepository::shift);
    }

    /**
     * Ближайшая к моменту смена, пересекающая {@code [dayStart, dayEnd)}.
     *
     * @param workerId рабочий
     * @param dayStart начало дня площадки
     * @param dayEnd   конец дня площадки
     * @param moment   момент, к которому ищем ближайшую
     * @return смена
     */
    public Optional<Shift> nearestOnDay(String workerId, Instant dayStart, Instant dayEnd, Instant moment) {
        return columns().from(SHIFTS)
                .where(SHIFTS.WORKER_ID.eq(workerId))
                .and(SHIFTS.STARTS_AT.lt(utc(dayEnd)))
                .and(SHIFTS.ENDS_AT.gt(utc(dayStart)))
                .orderBy(DSL.abs(DSL.extract(SHIFTS.STARTS_AT, DatePart.EPOCH)
                        .minus(DSL.extract(DSL.val(utc(moment)), DatePart.EPOCH))))
                .limit(1)
                .fetchOptional(ShiftReadRepository::shift);
    }

    /**
     * Все шаблоны по названию.
     *
     * @return шаблоны
     */
    public List<ShiftTemplate> templates() {
        return dsl.select(TEMPLATES.ID, TEMPLATES.NAME, TEMPLATES.START_TIME, TEMPLATES.END_TIME)
                .from(TEMPLATES)
                .orderBy(TEMPLATES.NAME)
                .fetch(row -> new ShiftTemplate(row.value1(), row.value2(), row.value3(), row.value4()));
    }

    private SelectSelectStep<Record5<UUID, String, OffsetDateTime, OffsetDateTime, UUID>> columns() {
        return dsl.select(SHIFTS.ID, SHIFTS.WORKER_ID, SHIFTS.STARTS_AT, SHIFTS.ENDS_AT, SHIFTS.TEMPLATE_ID);
    }

    private static Condition where(ShiftFilter filter) {
        List<Condition> conditions = new ArrayList<>();
        if (filter.workerId() != null) {
            conditions.add(SHIFTS.WORKER_ID.eq(filter.workerId()));
        }
        if (filter.period().from() != null) {
            conditions.add(SHIFTS.STARTS_AT.ge(utc(filter.period().from())));
        }
        if (filter.period().until() != null) {
            conditions.add(SHIFTS.STARTS_AT.lt(utc(filter.period().until())));
        }
        return DSL.and(conditions);
    }

    private static Shift shift(Record5<UUID, String, OffsetDateTime, OffsetDateTime, UUID> row) {
        return new Shift(row.value1(), row.value2(), row.value3().toInstant(), row.value4().toInstant(), row.value5());
    }

    private static OffsetDateTime utc(Instant moment) {
        return moment.atOffset(ZoneOffset.UTC);
    }
}
