package ru.documents.review.api.controller.impl;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.documents.review.api.controller.DocumentsController;
import ru.documents.review.api.dto.request.FieldsRq;
import ru.documents.review.api.dto.request.TypeRq;
import ru.documents.review.api.mapper.CardMapper;
import ru.documents.review.features.workplace.application.service.WorkplaceService;
import ru.documents.review.features.workplace.model.Actor;

/**
 * Действия с документом.
 */
@RestController
public class DocumentsControllerImpl implements DocumentsController {

    private final WorkplaceService workplace;
    private final CardMapper mapper;

    public DocumentsControllerImpl(WorkplaceService workplace, CardMapper mapper) {
        this.workplace = workplace;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Void> fields(Authentication auth, UUID id, FieldsRq rq) {
        workplace.correct(id, mapper.edits(rq), Actor.of(auth));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> type(Authentication auth, UUID id, TypeRq rq) {
        workplace.retype(id, rq.type(), Actor.of(auth));
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> confirm(Authentication auth, UUID id) {
        workplace.confirm(id, Actor.of(auth));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> reprocess(Authentication auth, UUID id) {
        workplace.reprocess(id, Actor.of(auth));
        return ResponseEntity.accepted().build();
    }
}
