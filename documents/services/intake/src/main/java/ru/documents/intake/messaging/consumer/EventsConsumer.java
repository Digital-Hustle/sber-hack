package ru.documents.intake.messaging.consumer;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import ru.documents.common.event.Topics;
import ru.documents.intake.features.applications.command.service.DocumentEventsService;
import ru.documents.intake.features.stream.api.StreamHub;

/**
 * Стадии от docproc и решения review → документы заявки и SSE соискателю.
 */
@Component
public class EventsConsumer {

    private final DocumentEventsService service;
    private final StreamHub hub;
    private final ObjectMapper json;

    public EventsConsumer(DocumentEventsService service, StreamHub hub, ObjectMapper json) {
        this.service = service;
        this.hub = hub;
        this.json = json;
    }

    /** Topic docproc.document.v1. */
    @KafkaListener(topics = Topics.DOCPROC_DOCUMENT_V1, groupId = "intake")
    public void docproc(String message) throws IOException {
        JsonNode env = json.readTree(message);
        if (!"StageChanged".equals(env.path("type").asText())) {
            return;
        }
        JsonNode p = env.path("payload");
        service.stage(env.path("eventId").asText(), p).ifPresent(app -> hub.publish(app, "document.stage",
                Map.of("documentId", p.path("documentId").asText(), "stage", p.path("stage").asText())));
    }

    /** Topic review.application.v1. */
    @KafkaListener(topics = Topics.REVIEW_APPLICATION_V1, groupId = "intake")
    public void review(String message) throws IOException {
        JsonNode env = json.readTree(message);
        String type = env.path("type").asText();
        service.review(env.path("eventId").asText(), type, env.path("payload"))
                .ifPresent(app -> hub.publish(app, "application.changed", Map.of("type", type)));
    }
}
