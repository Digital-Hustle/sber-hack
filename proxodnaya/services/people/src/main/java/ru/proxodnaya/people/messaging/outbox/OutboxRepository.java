package ru.proxodnaya.people.messaging.outbox;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import ru.proxodnaya.people.jooq.Tables;

/**
 * Таблица outbox.
 */
@Repository
public class OutboxRepository {

    private static final ru.proxodnaya.people.jooq.tables.Outbox OUTBOX = Tables.OUTBOX;

    private final DSLContext dsl;

    public OutboxRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Неотправленные события, заблокированные для этой транзакции.
     *
     * @param limit сколько взять
     * @return события по порядку
     */
    public List<OutboxMessage> lockUnpublished(int limit) {
        return dsl.select(OUTBOX.ID, OUTBOX.TOPIC, OUTBOX.MESSAGE_KEY, OUTBOX.PAYLOAD)
                .from(OUTBOX)
                .where(OUTBOX.PUBLISHED_AT.isNull())
                .orderBy(OUTBOX.CREATED_AT)
                .limit(limit)
                .forUpdate()
                .skipLocked()
                .fetch(row -> new OutboxMessage(row.value1(), row.value2(), row.value3(), row.value4().data()));
    }

    /**
     * Добавляет событие.
     *
     * @param id      id события
     * @param topic   топик
     * @param key     ключ сообщения
     * @param json    конверт события
     * @param moment      момент
     */
    public void insert(UUID id, String topic, String key, String json, Instant moment) {
        dsl.insertInto(OUTBOX)
                .set(OUTBOX.ID, id)
                .set(OUTBOX.TOPIC, topic)
                .set(OUTBOX.MESSAGE_KEY, key)
                .set(OUTBOX.PAYLOAD, JSONB.valueOf(json))
                .set(OUTBOX.CREATED_AT, moment.atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Отмечает отправленным.
     *
     * @param id событие
     * @param moment момент отправки
     */
    public void markPublished(UUID id, Instant moment) {
        dsl.update(OUTBOX).set(OUTBOX.PUBLISHED_AT, moment.atOffset(ZoneOffset.UTC)).where(OUTBOX.ID.eq(id)).execute();
    }

    /**
     * Событие к отправке.
     *
     * @param id      id
     * @param topic   топик
     * @param key     ключ
     * @param payload JSON-конверт
     */
    public record OutboxMessage(UUID id, String topic, String key, String payload) {
    }
}
