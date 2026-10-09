package ru.proxodnaya.shift.features.shift.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.shift.model.Shift;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * Запись смен.
 */
@Repository
public class ShiftRepository {

    private static final ru.proxodnaya.shift.jooq.tables.Shifts SHIFTS = Tables.SHIFTS;

    private final DSLContext dsl;

    public ShiftRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Блокирует смены рабочего до конца транзакции: проверка пересечения и вставка не гоняются между запросами.
     *
     * @param workerId рабочий
     */
    public void lockWorker(String workerId) {
        dsl.execute("select pg_advisory_xact_lock(hashtextextended({0}, 0))", DSL.val("shift:" + workerId));
    }

    /**
     * Есть ли у рабочего смена, пересекающая {@code [start, end)}.
     *
     * @param workerId рабочий
     * @param start    начало
     * @param end      конец
     * @return {@code true}, если пересекается
     */
    public boolean overlaps(String workerId, Instant start, Instant end) {
        return dsl.fetchExists(SHIFTS, SHIFTS.WORKER_ID.eq(workerId)
                .and(SHIFTS.STARTS_AT.lt(end.atOffset(ZoneOffset.UTC)))
                .and(SHIFTS.ENDS_AT.gt(start.atOffset(ZoneOffset.UTC))));
    }

    /**
     * Сохраняет смену.
     *
     * @param shift   смена
     * @param actorId кто назначил
     * @param moment  когда
     */
    public void insert(Shift shift, String actorId, Instant moment) {
        dsl.insertInto(SHIFTS)
                .set(SHIFTS.ID, shift.id())
                .set(SHIFTS.WORKER_ID, shift.workerId())
                .set(SHIFTS.STARTS_AT, shift.start().atOffset(ZoneOffset.UTC))
                .set(SHIFTS.ENDS_AT, shift.end().atOffset(ZoneOffset.UTC))
                .set(SHIFTS.TEMPLATE_ID, shift.templateId())
                .set(SHIFTS.CREATED_BY, actorId)
                .set(SHIFTS.CREATED_AT, moment.atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Удаляет смену.
     *
     * @param id смена
     * @return {@code true}, если была
     */
    public boolean delete(UUID id) {
        return dsl.deleteFrom(SHIFTS).where(SHIFTS.ID.eq(id)).execute() == 1;
    }
}
