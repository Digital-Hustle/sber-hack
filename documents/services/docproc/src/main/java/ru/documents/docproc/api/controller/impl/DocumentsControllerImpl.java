package ru.documents.docproc.api.controller.impl;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ru.documents.docproc.api.controller.DocumentsController;
import ru.documents.docproc.api.dto.response.ModelStatusRs;
import ru.documents.docproc.api.dto.response.QueueStatsRs;
import ru.documents.docproc.api.dto.response.ResultRs;
import ru.documents.docproc.api.dto.response.TypeRs;
import ru.documents.docproc.api.mapper.ResultMapper;
import ru.documents.docproc.features.processing.command.service.JobCommandService;
import ru.documents.docproc.features.processing.query.service.ResultQueryService;
import ru.documents.docproc.features.processing.query.service.TypeRegistry;
import ru.documents.llm.api.LlmGateway;
import ru.documents.llm.api.LlmStatus;

/**
 * Реализация API оператора docproc.
 */
@RestController
public class DocumentsControllerImpl implements DocumentsController {

    private final ResultQueryService queries;
    private final JobCommandService commands;
    private final TypeRegistry types;
    private final LlmGateway llm;
    private final ResultMapper mapper;

    public DocumentsControllerImpl(ResultQueryService queries, JobCommandService commands, TypeRegistry types,
                                   LlmGateway llm, ResultMapper mapper) {
        this.queries = queries;
        this.commands = commands;
        this.types = types;
        this.llm = llm;
        this.mapper = mapper;
    }

    @Override
    public ResultRs result(UUID id) {
        return mapper.convert(queries.result(id));
    }

    @Override
    public ResponseEntity<Void> reprocess(UUID id) {
        commands.reprocess(id);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ModelStatusRs model() {
        LlmStatus s = llm.status();
        return new ModelStatusRs(s.provider(), s.model(), s.visionModel(), s.reachable(), s.checkedAt(), s.error());
    }

    @Override
    public QueueStatsRs stats() {
        var s = queries.stats();
        return new QueueStatsRs(s.waiting(), s.inProgress(), s.parsed(), s.needsReview(), s.unreadable(), s.waitingModel(),
                s.failed(), s.avgSecondsByStage(), s.p95Seconds());
    }

    @Override
    public TypeRs types() {
        return new TypeRs(types.all().stream().map(t -> new TypeRs.Item(t.code(), t.name(), t.version())).toList());
    }
}
