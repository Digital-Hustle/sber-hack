package ru.proxodnaya.access.messaging.consumer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.proxodnaya.access.jooq.Tables;

/**
 * Обработанные события (идемпотентность потребителей).
 */
@Repository
public class ProcessedEventRepository {

    private static final ru.proxodnaya.access.jooq.tables.ProcessedEvents PROCESSED_EVENTS = Tables.PROCESSED_EVENTS;

    private final DSLContext dsl;

    public ProcessedEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Отмечает событие обработанным.
     *
     * @param eventId событие
     * @param moment      момент
     * @return {@code true}, если событие видим впервые
     */
    public boolean markProcessed(UUID eventId, Instant moment) {
        return dsl.insertInto(PROCESSED_EVENTS)
                .set(PROCESSED_EVENTS.EVENT_ID, eventId)
                .set(PROCESSED_EVENTS.PROCESSED_AT, moment.atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute() == 1;
    }
}
