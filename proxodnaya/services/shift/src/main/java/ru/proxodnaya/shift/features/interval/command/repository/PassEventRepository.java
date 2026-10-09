package ru.proxodnaya.shift.features.interval.command.repository;

import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.interval.model.PassEvent;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * Проекция попыток прохода.
 */
@Repository
public class PassEventRepository {

    private static final ru.proxodnaya.shift.jooq.tables.PassEvents PASS_EVENTS = Tables.PASS_EVENTS;

    private final DSLContext dsl;

    public PassEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сохраняет попытку, если её ещё нет.
     *
     * @param event попытка
     * @return {@code true}, если записана впервые
     */
    public boolean insert(PassEvent event) {
        return dsl.insertInto(PASS_EVENTS)
                .set(PASS_EVENTS.ATTEMPT_ID, event.attemptId())
                .set(PASS_EVENTS.WORKER_ID, event.workerId())
                .set(PASS_EVENTS.CHECKPOINT_ID, event.checkpointId())
                .set(PASS_EVENTS.ZONE_ID, event.zoneId())
                .set(PASS_EVENTS.DIRECTION, event.direction())
                .set(PASS_EVENTS.DECISION, event.decision())
                .set(PASS_EVENTS.REASON_CODE, event.reasonCode())
                .set(PASS_EVENTS.SOURCE, event.source())
                .set(PASS_EVENTS.TS, event.ts().atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute() == 1;
    }
}
