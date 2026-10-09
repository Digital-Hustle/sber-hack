package ru.documents.common.event;

import java.time.Instant;
import java.util.Map;

/**
 * Конверт события Kafka. Ключ сообщения — aggregateId (обычно id заявки).
 *
 * @param eventId     уникальный id (UUIDv7)
 * @param type        тип события
 * @param occurredAt  момент события
 * @param aggregateId id агрегата
 * @param traceparent W3C trace context
 * @param payload     данные события
 */
public record EventEnvelope(
        String eventId,
        String type,
        Instant occurredAt,
        String aggregateId,
        String traceparent,
        Map<String, Object> payload
) {
}
