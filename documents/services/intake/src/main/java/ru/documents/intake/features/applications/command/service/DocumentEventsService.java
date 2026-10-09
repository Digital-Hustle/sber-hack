package ru.documents.intake.features.applications.command.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.documents.common.inbox.IdempotentConsumer;
import ru.documents.intake.features.applications.command.repository.ApplicationRepository;

/**
 * Применяет события docproc (стадии) и review (запросы, решения) к заявке. Идемпотентно по eventId.
 */
@Service
public class DocumentEventsService {

    private static final String DOC = "documentId";
    private static final String APP = "applicationId";

    private final ApplicationRepository repo;
    private final IdempotentConsumer inbox;
    private final Clock clock;

    public DocumentEventsService(ApplicationRepository repo, IdempotentConsumer inbox, Clock clock) {
        this.repo = repo;
        this.inbox = inbox;
        this.clock = clock;
    }

    /**
     * Стадия документа от docproc (FR-12, FR-13).
     *
     * @param eventId id события
     * @param p       payload
     * @return заявка, которой надо отправить SSE
     */
    @Transactional
    public Optional<UUID> stage(String eventId, JsonNode p) {
        if (!inbox.firstTime(eventId)) {
            return Optional.empty();
        }
        UUID doc = UUID.fromString(p.path(DOC).asText());
        String type = text(p, "type");
        String reason = text(p, "reasonCode");
        String params = p.has("reasonParams") ? p.get("reasonParams").toString() : null;
        Instant now = clock.instant();
        if (type != null && !"UNKNOWN".equals(type)) {
            String slot = repo.slot(doc).orElse(null);
            if (slot != null && !slot.equals(type) && reason == null) {
                repo.moveSlot(doc, type);
                reason = "TYPE_MOVED";
                params = "{\"from\":\"" + slot + "\",\"to\":\"" + type + "\"}";
            } else if (slot == null) {
                repo.moveSlot(doc, type);
            }
        }
        return repo.updateStage(doc, p.path("stage").asText(), "UNKNOWN".equals(type) ? null : type, reason, params,
                now);
    }

    /**
     * Событие review: назначение, запрос дослать, решение (FR-55, B-01).
     *
     * @param eventId id события
     * @param type    тип события
     * @param p       payload
     * @return заявка для SSE
     */
    @Transactional
    public Optional<UUID> review(String eventId, String type, JsonNode p) {
        if (!inbox.firstTime(eventId) || !p.hasNonNull(APP)) {
            return Optional.empty();
        }
        UUID app = UUID.fromString(p.get(APP).asText());
        Instant now = clock.instant();
        switch (type) {
            case "ApplicationAssigned" -> repo.setStatus(app, "IN_REVIEW", null, now);
            case "ResubmitRequested" -> {
                for (JsonNode it : p.path("items")) {
                    UUID doc = it.hasNonNull(DOC) ? UUID.fromString(it.get(DOC).asText()) : null;
                    repo.insertRequest(UuidCreator.getTimeOrderedEpoch(), app, doc, text(it, "type"),
                            it.path("reason").asText(""), now);
                }
                repo.setStatus(app, "NEEDS_CHANGES", null, now);
            }
            case "ApplicationDecided" -> repo.setStatus(app, "ACCEPTED".equals(p.path("decision").asText())
                    ? "ACCEPTED" : "RETURNED", text(p, "comment"), now);
            default -> {
                return Optional.empty();
            }
        }
        return Optional.of(app);
    }

    private static String text(JsonNode n, String f) {
        return n.hasNonNull(f) && !n.get(f).asText().isBlank() ? n.get(f).asText() : null;
    }
}
