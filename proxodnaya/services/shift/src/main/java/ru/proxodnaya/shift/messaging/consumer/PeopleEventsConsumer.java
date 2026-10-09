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
import ru.proxodnaya.shift.features.worker.command.service.WorkerDirectoryService;
import ru.proxodnaya.shift.features.worker.model.WorkerCard;

/**
 * {@code people.worker.v1} → проекция карточек рабочих (ФИО, должность, подрядчик для табеля). Ключ — workerId,
 * события одного рабочего приходят по порядку. Ошибка — 3 повтора и DLT.
 */
@Component
public class PeopleEventsConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PeopleEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final IdempotentConsumer idempotent;
    private final WorkerDirectoryService directory;

    public PeopleEventsConsumer(ObjectMapper objectMapper, IdempotentConsumer idempotent, WorkerDirectoryService directory) {
        this.objectMapper = objectMapper;
        this.idempotent = idempotent;
        this.directory = directory;
    }

    /**
     * Принимает событие people.
     *
     * @param message JSON-конверт события
     * @throws JsonProcessingException если конверт не JSON (уйдёт в DLT)
     */
    @KafkaListener(topics = Topics.PEOPLE_WORKER_V1, groupId = "shift")
    public void on(String message) throws JsonProcessingException {
        JsonNode event = objectMapper.readTree(message);
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        String type = event.path("type").asText();
        Instant moment = Instant.parse(event.path("occurredAt").asText());
        JsonNode payload = event.path("payload");
        idempotent.once(eventId, () -> apply(type, payload, moment));
    }

    private void apply(String type, JsonNode payload, Instant moment) {
        String workerId = payload.path("workerId").asText();
        switch (type) {
            case "WorkerCreated", "WorkerUpdated" -> directory.save(new WorkerCard(workerId, text(payload, "fullName"),
                    text(payload, "position"), text(payload, "contractor"), payload.path("status").asText("ACTIVE")), moment);
            case "WorkerBlocked" -> directory.block(workerId, moment);
            case "WorkerDeleted" -> directory.delete(workerId, moment);
            case null, default -> LOG.debug("people: событие {} shift не нужно", type);
        }
    }

    private static String text(JsonNode payload, String field) {
        JsonNode node = payload.path(field);
        return node.isNull() || node.isMissingNode() ? null : node.asText();
    }
}
