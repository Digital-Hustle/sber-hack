package ru.documents.review.api.controller.impl;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.documents.review.api.controller.ApplicationsController;
import ru.documents.review.api.dto.request.DecisionRq;
import ru.documents.review.api.dto.request.ResubmitRq;
import ru.documents.review.api.dto.request.RevealRq;
import ru.documents.review.api.dto.response.CardRs;
import ru.documents.review.api.dto.response.HistoryRs;
import ru.documents.review.api.dto.response.NextRs;
import ru.documents.review.api.dto.response.QueueRs;
import ru.documents.review.api.dto.response.RevealRs;
import ru.documents.review.api.mapper.CardMapper;
import ru.documents.review.features.workplace.application.service.ExportService;
import ru.documents.review.features.workplace.application.service.WorkplaceService;
import ru.documents.review.features.workplace.command.service.ActionService;
import ru.documents.review.features.workplace.model.Actor;
import ru.documents.review.features.workplace.query.service.QueryService;

/**
 * Очередь и карточка.
 */
@RestController
public class ApplicationsControllerImpl implements ApplicationsController {

    private final WorkplaceService workplace;
    private final ActionService actions;
    private final QueryService queries;
    private final ExportService exports;
    private final CardMapper mapper;

    public ApplicationsControllerImpl(WorkplaceService workplace, ActionService actions, QueryService queries,
                                      ExportService exports, CardMapper mapper) {
        this.workplace = workplace;
        this.actions = actions;
        this.queries = queries;
        this.exports = exports;
        this.mapper = mapper;
    }

    @Override
    public QueueRs queue(Authentication auth, List<String> status, String q, String assignee, boolean attention,
                         String sort, String cursor, int limit) {
        return mapper.queue(queries.queue(status == null ? List.of() : status, q, assignee, attention, sort, cursor,
                limit, Actor.of(auth)));
    }

    @Override
    public NextRs next(Authentication auth) {
        return new NextRs(workplace.next(Actor.of(auth)));
    }

    @Override
    public ResponseEntity<Void> assign(Authentication auth, UUID id, boolean force) {
        workplace.assign(id, Actor.of(auth), force);
        return ResponseEntity.noContent().build();
    }

    @Override
    public CardRs card(UUID id) {
        return mapper.card(workplace.card(id, false));
    }

    @Override
    public HistoryRs history(Authentication auth, UUID id) {
        return new HistoryRs(queries.history(id, Actor.of(auth)));
    }

    @Override
    public ResponseEntity<Void> request(Authentication auth, UUID id, ResubmitRq rq) {
        workplace.request(id, mapper.items(rq), Actor.of(auth));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> decision(Authentication auth, UUID id, DecisionRq rq) {
        workplace.decide(id, rq.decision(), rq.comment(), Actor.of(auth));
        return ResponseEntity.noContent().build();
    }

    @Override
    public RevealRs reveal(Authentication auth, UUID id, RevealRq rq) {
        return new RevealRs(actions.reveal(id, rq.documentId(), rq.field(), Actor.of(auth)));
    }

    @Override
    public ResponseEntity<byte[]> export(Authentication auth, UUID id, String format) {
        return file(exports.card(id, format, Actor.of(auth)));
    }

    static ResponseEntity<byte[]> file(ExportService.FileOut f) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.name(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(f.mime())).body(f.body());
    }
}
