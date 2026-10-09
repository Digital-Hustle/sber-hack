package ru.documents.docproc.messaging.consumer;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ru.documents.common.event.Topics;
import ru.documents.common.inbox.IdempotentConsumer;
import ru.documents.docproc.features.processing.command.service.JobCommandService;

/**
 * События intake.document.v1 → задания очереди (идемпотентно по eventId).
 */
@Component
public class IntakeEventsConsumer {

    private static final String DOC = "documentId";
    private static final String APP = "applicationId";

    private final JobCommandService commands;
    private final IdempotentConsumer inbox;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public IntakeEventsConsumer(JobCommandService commands, IdempotentConsumer inbox, TransactionTemplate tx,
                                ObjectMapper json) {
        this.commands = commands;
        this.inbox = inbox;
        this.tx = tx;
        this.json = json;
    }

    /** Топик intake.document.v1. */
    @KafkaListener(topics = Topics.INTAKE_DOCUMENT_V1, groupId = "docproc")
    public void onMessage(String message) throws IOException {
        JsonNode env = json.readTree(message);
        handle(env.path("eventId").asText(), env.path("type").asText(), env.path("payload"));
    }

    /** Обработать событие (одна транзакция с отметкой в processed_events). */
    public void handle(String eventId, String type, JsonNode p) {
        tx.executeWithoutResult(s -> {
            if (!inbox.firstTime(eventId) || !p.hasNonNull(DOC)) {
                return;
            }
            UUID doc = UUID.fromString(p.get(DOC).asText());
            switch (type) {
                case "DocumentReceived" -> commands.enqueue(doc, UUID.fromString(p.get(APP).asText()),
                        p.path("version").asInt(1));
                case "FilesChanged", "FileReplaced" -> commands.filesChanged(doc, UUID.fromString(p.get(APP).asText()),
                        p.path("version").asInt(1));
                case "DocumentRemoved" -> commands.removed(doc);
                default -> {
                    // Чужие события игнорируем.
                }
            }
        });
    }
}
