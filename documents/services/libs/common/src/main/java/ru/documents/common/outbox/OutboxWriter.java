package ru.documents.common.outbox;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.documents.common.event.EventEnvelope;

/**
 * Transactional outbox: событие пишется в таблицу outbox в той же транзакции, что и изменение данных.
 * Отправку в Kafka делает {@link OutboxRelay}.
 */
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public OutboxWriter(JdbcClient jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Добавляет событие в outbox (обязательно внутри текущей транзакции).
     *
     * @param topic       топик
     * @param type        тип события
     * @param aggregateId ключ сообщения
     * @param payload     данные
     * @return id события
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID add(String topic, String type, String aggregateId, Map<String, Object> payload) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        EventEnvelope env = new EventEnvelope(id.toString(), type, clock.instant(), aggregateId, null, payload);
        String body;
        try {
            body = json.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("event serialization failed", e);
        }
        jdbc.sql("insert into outbox(id, topic, msg_key, payload, created_at) values (?, ?, ?, ?::jsonb, ?)")
                .params(id, topic, aggregateId, body, java.sql.Timestamp.from(clock.instant()))
                .update();
        return id;
    }
}
