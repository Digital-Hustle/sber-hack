package ru.proxodnaya.access.messaging.outbox;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.common.event.EventEnvelope;

/**
 * Transactional outbox: событие пишется в ту же транзакцию, что и изменение. В Kafka его отправит {@link OutboxRelay}.
 */
@Component
public class Outbox {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public Outbox(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Добавляет событие.
     *
     * @param topic       топик
     * @param type        тип события
     * @param aggregateId id агрегата, он же ключ сообщения
     * @param payload     данные события
     * @throws IllegalStateException если событие не сериализуется в JSON
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String topic, String type, String aggregateId, Map<String, Object> payload) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        EventEnvelope envelope = new EventEnvelope(id.toString(), type, clock.instant(), aggregateId, null, payload);
        try {
            repository.insert(id, topic, aggregateId, objectMapper.writeValueAsString(envelope), clock.instant());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Событие " + type + " не сериализуется", error);
        }
    }
}
