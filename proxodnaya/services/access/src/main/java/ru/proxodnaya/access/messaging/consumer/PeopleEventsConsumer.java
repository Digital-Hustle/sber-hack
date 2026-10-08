package ru.proxodnaya.access.messaging.consumer;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.proxodnaya.access.features.pass.api.WorkerStatusProjection;
import ru.proxodnaya.access.features.qr.api.DeviceKeyProjection;
import ru.proxodnaya.common.event.Topics;

/**
 * Проекции из people (ACC-04): {@code people.device.v1} → device_keys, {@code people.worker.v1} → статус рабочего.
 * Ключ сообщения — id агрегата, поэтому события одного устройства/рабочего приходят по порядку.
 */
@Component
public class PeopleEventsConsumer {

    private static final String DEVICE_ID = "deviceId";
    private static final String WORKER_ID = "workerId";
    private static final Logger LOG = LoggerFactory.getLogger(PeopleEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final IdempotentConsumer idempotent;
    private final DeviceKeyProjection devices;
    private final WorkerStatusProjection workers;

    public PeopleEventsConsumer(ObjectMapper objectMapper, IdempotentConsumer idempotent,
                                DeviceKeyProjection devices, WorkerStatusProjection workers) {
        this.objectMapper = objectMapper;
        this.idempotent = idempotent;
        this.devices = devices;
        this.workers = workers;
    }

    /**
     * Принимает событие people.
     *
     * @param message JSON-конверт события
     * @throws JsonProcessingException если конверт не JSON (уйдёт в DLT)
     */
    @KafkaListener(topics = {Topics.PEOPLE_DEVICE_V1, Topics.PEOPLE_WORKER_V1}, groupId = "access")
    public void on(String message) throws JsonProcessingException {
        JsonNode event = objectMapper.readTree(message);
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        String type = event.path("type").asText();
        Instant moment = Instant.parse(event.path("occurredAt").asText());
        JsonNode payload = event.path("payload");
        idempotent.once(eventId, () -> apply(type, payload, moment));
    }

    private void apply(String type, JsonNode payload, Instant moment) {
        String deviceId = payload.path(DEVICE_ID).asText();
        String workerId = payload.path(WORKER_ID).asText();
        switch (type) {
            case "DeviceBound" -> devices.bind(deviceId, workerId, publicKey(payload.path("publicKey")), moment);
            case "DeviceRevoked" -> devices.revoke(deviceId, moment);
            case "WorkerCreated", "WorkerUpdated" -> workers.updateStatus(workerId, payload.path("status").asText("ACTIVE"), moment);
            case "WorkerBlocked" -> workers.updateStatus(workerId, "BLOCKED", moment);
            case "WorkerDeleted" -> workers.updateStatus(workerId, "DELETED", moment);
            case null, default -> LOG.debug("people: событие {} access не нужно", type);
        }
    }

    private static String publicKey(JsonNode node) {
        return node.isObject() ? node.toString() : node.asText();
    }
}
