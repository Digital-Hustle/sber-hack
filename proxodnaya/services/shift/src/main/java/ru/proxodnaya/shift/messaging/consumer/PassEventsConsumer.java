package ru.proxodnaya.shift.messaging.consumer;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.proxodnaya.common.event.Topics;
import ru.proxodnaya.shift.features.interval.command.service.IntervalService;
import ru.proxodnaya.shift.features.interval.model.PassEvent;

/**
 * {@code access.pass.v1} → проекция попыток и интервалы присутствия (SH-04). Ключ сообщения — workerId, поэтому
 * проходы одного человека приходят по порядку. Ошибка — 3 повтора и {@code access.pass.v1.DLT}.
 */
@Component
public class PassEventsConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PassEventsConsumer.class);
    private static final String PASS_DECIDED = "PassDecided";

    private final ObjectMapper objectMapper;
    private final IdempotentConsumer idempotent;
    private final IntervalService intervals;

    public PassEventsConsumer(ObjectMapper objectMapper, IdempotentConsumer idempotent, IntervalService intervals) {
        this.objectMapper = objectMapper;
        this.idempotent = idempotent;
        this.intervals = intervals;
    }

    /**
     * Принимает событие access.
     *
     * @param message JSON-конверт события
     * @throws JsonProcessingException если конверт не JSON (уйдёт в DLT)
     */
    @KafkaListener(topics = Topics.ACCESS_PASS_V1, groupId = "shift")
    public void on(String message) throws JsonProcessingException {
        JsonNode event = objectMapper.readTree(message);
        String type = event.path("type").asText();
        if (!PASS_DECIDED.equals(type)) {
            LOG.debug("access: событие {} shift не нужно", type);
            return;
        }
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        PassEvent pass = pass(event.path("payload"));
        idempotent.once(eventId, () -> intervals.onPass(pass));
    }

    private static PassEvent pass(JsonNode payload) {
        return new PassEvent(UUID.fromString(payload.path("attemptId").asText()), text(payload, "workerId"),
                text(payload, "checkpointId"), text(payload, "zoneId"), payload.path("direction").asText(),
                payload.path("decision").asText(), payload.path("reasonCode").asText(), payload.path("source").asText(),
                Instant.parse(payload.path("ts").asText()));
    }

    private static String text(JsonNode payload, String field) {
        JsonNode node = payload.path(field);
        return node.isNull() || node.isMissingNode() ? null : node.asText();
    }
}
