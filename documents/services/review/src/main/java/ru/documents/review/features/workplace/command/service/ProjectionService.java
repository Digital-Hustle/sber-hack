package ru.documents.review.features.workplace.command.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.documents.common.inbox.IdempotentConsumer;
import ru.documents.review.features.workplace.command.repository.ProjectionRepository;
import ru.documents.review.features.workplace.model.DocRow;
import ru.documents.review.features.workplace.model.StreamEvent;
import ru.documents.review.features.workplace.rules.CrossChecker;
import ru.documents.review.features.workplace.rules.DocFacts;
import ru.documents.review.features.workplace.rules.Fields;
import ru.documents.review.features.workplace.persistence.RowMappers;

/**
 * Проекция из событий intake и docproc (идемпотентно, processed_events в той же транзакции).
 */
@Service
public class ProjectionService {

    private static final String APP = "applicationId";
    private static final String DOC = "documentId";
    private static final String SYSTEM = "system";

    private final ProjectionRepository repo;
    private final IdempotentConsumer inbox;
    private final HistoryWriter history;
    private final RowMappers rows;
    private final Clock clock;

    public ProjectionService(ProjectionRepository repo, IdempotentConsumer inbox, HistoryWriter history,
                             RowMappers rows, Clock clock) {
        this.repo = repo;
        this.inbox = inbox;
        this.history = history;
        this.rows = rows;
        this.clock = clock;
    }

    /**
     * Применить событие.
     *
     * @param eventId id события
     * @param type    тип
     * @param p       payload
     * @return что сообщить операторам по SSE
     */
    @Transactional
    public Optional<StreamEvent> apply(String eventId, String type, JsonNode p) {
        if (!inbox.firstTime(eventId) || !p.hasNonNull(APP)) {
            return Optional.empty();
        }
        UUID app = UUID.fromString(p.get(APP).asText());
        UUID doc = p.hasNonNull(DOC) ? UUID.fromString(p.get(DOC).asText()) : null;
        Instant now = clock.instant();
        switch (type) {
            case "ApplicationCreated" -> repo.created(app, text(p, "source"),
                    p.hasNonNull("createdAt") ? Instant.parse(p.get("createdAt").asText()) : now, now);
            case "ProfileChanged" -> repo.profile(app, strings(p.path("requiredTypes")), now);
            case "Submitted" -> {
                repo.submitted(app, strings(p.path("missing")), now);
                history.add(app, null, "applicant", HistoryWriter.APPLICANT, "SUBMITTED",
                        Map.of("missing", strings(p.path("missing"))));
            }
            case "DocumentReceived" -> {
                repo.received(doc, app, text(p, "slotType"), now);
                history.add(app, doc, "applicant", HistoryWriter.APPLICANT, "DOCUMENT_UPLOADED",
                        details("slotType", text(p, "slotType")));
            }
            case "FilesChanged", "FileReplaced" -> {
                repo.filesChanged(doc, app, now);
                history.add(app, doc, "applicant", HistoryWriter.APPLICANT, "FILES_CHANGED",
                        details("mode", text(p, "mode")));
                derive(app);
            }
            case "DocumentRemoved" -> {
                repo.removed(doc, now);
                history.add(app, doc, "applicant", HistoryWriter.APPLICANT, "DOCUMENT_REMOVED", Map.of());
                derive(app);
            }
            case "StageChanged" -> {
                String stage = text(p, "stage");
                repo.stage(doc, app, stage, text(p, "type"), text(p, "reasonCode"), now);
                return Optional.of(new StreamEvent("document.stage", app, doc, stage));
            }
            case "DocumentParsed", "DocumentUnreadable", "DocumentFailed" -> {
                String stage = text(p, "stage");
                Double conf = p.hasNonNull("typeConfidence") ? p.get("typeConfidence").asDouble() : null;
                repo.finished(doc, app, stage, text(p, "type"), conf, rows.write(p.path("reasons")),
                        text(p, "resultId"), now);
                Map<String, Object> d = details("stage", stage);
                d.put("type", text(p, "type"));
                d.put("model", text(p, "model"));
                history.add(app, doc, SYSTEM, HistoryWriter.MODEL, "DOCUMENT_PARSED", d);
                return Optional.of(new StreamEvent("document.stage", app, doc, stage));
            }
            default -> {
                return Optional.empty();
            }
        }
        return Optional.of(StreamEvent.queue(app));
    }

    /**
     * Положить результат docproc в кэш карточки и пересчитать производные.
     *
     * @param doc    документ
     * @param result результат
     */
    @Transactional
    public void cache(UUID doc, JsonNode result) {
        Optional<DocRow> d = repo.doc(doc);
        if (d.isEmpty()) {
            return;
        }
        String type = result.hasNonNull("type") ? result.get("type").asText() : null;
        repo.cacheResult(doc, rows.write(result), text(result, "id"), type);
        derive(d.get().applicationId());
    }

    /**
     * Пересчитать ФИО соискателя и сроки действия документов заявки.
     *
     * @param app заявка
     */
    @Transactional
    public void derive(UUID app) {
        List<DocFacts> facts = new ArrayList<>();
        for (DocRow d : repo.docs(app)) {
            DocFacts f = Fields.facts(d, repo.overrides(d.id()));
            facts.add(f);
            repo.expires(d.id(), CrossChecker.expiresOn(f).orElse(null));
        }
        repo.applicantName(app, CrossChecker.applicantName(facts));
    }

    private static Map<String, Object> details(String k, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static String text(JsonNode p, String k) {
        return p.hasNonNull(k) ? p.get(k).asText() : null;
    }
}
