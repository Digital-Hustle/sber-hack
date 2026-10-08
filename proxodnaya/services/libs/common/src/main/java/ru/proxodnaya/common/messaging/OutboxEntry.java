package ru.proxodnaya.common.messaging;

import java.time.Instant;
import java.util.Map;

/**
 * Запись в таблице outbox. Публикуется OutboxRelay в Kafka.
 * Создаётся в той же транзакции, что и изменение агрегата.
 *
 * @param id          UUIDv7 (ключ сообщения = aggregateId)
 * @param topic       топик Kafka
 * @param messageKey  ключ сообщения (aggregateId)
 * @param payload     JSON события
 * @param createdAt   момент создания
 * @param publishedAt момент публикации (null = не опубликовано)
 */
public record OutboxEntry(
        java.util.UUID id,
        String topic,
        String messageKey,
        Map<String, Object> payload,
        Instant createdAt,
        Instant publishedAt
) {
    public static OutboxEntry of(String topic, String messageKey, Map<String, Object> payload) {
        return new OutboxEntry(
                com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch(),
                topic,
                messageKey,
                payload,
                Instant.now(),
                null
        );
    }

    public OutboxEntry markPublished() {
        return new OutboxEntry(id, topic, messageKey, payload, createdAt, Instant.now());
    }
}
