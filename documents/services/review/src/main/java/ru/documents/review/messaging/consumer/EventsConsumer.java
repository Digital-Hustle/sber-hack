package ru.documents.review.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.documents.common.event.Topics;
import ru.documents.review.features.workplace.application.service.WorkplaceService;

/**
 * События intake и docproc → проекция очереди.
 */
@Component
public class EventsConsumer {

    private final WorkplaceService workplace;
    private final ObjectMapper json;

    public EventsConsumer(WorkplaceService workplace, ObjectMapper json) {
        this.workplace = workplace;
        this.json = json;
    }

    /** Топики intake.application.v1, intake.document.v1, docproc.document.v1. */
    @KafkaListener(topics = {Topics.INTAKE_APPLICATION_V1, Topics.INTAKE_DOCUMENT_V1, Topics.DOCPROC_DOCUMENT_V1},
            groupId = "review")
    public void onMessage(String message) throws IOException {
        JsonNode env = json.readTree(message);
        workplace.onEvent(env.path("eventId").asText(), env.path("type").asText(), env.path("payload"));
    }
}
