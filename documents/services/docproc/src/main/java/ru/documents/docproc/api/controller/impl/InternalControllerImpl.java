package ru.documents.docproc.api.controller.impl;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ru.documents.common.exception.DomainException;
import ru.documents.docproc.api.controller.InternalController;
import ru.documents.docproc.api.dto.request.RetypeRq;
import ru.documents.docproc.api.dto.response.ResultRs;
import ru.documents.docproc.api.mapper.ResultMapper;
import ru.documents.docproc.features.processing.command.service.JobCommandService;
import ru.documents.docproc.features.processing.query.service.ResultQueryService;
import ru.documents.docproc.features.processing.query.service.TypeRegistry;

/**
 * Реализация внутреннего API.
 */
@RestController
public class InternalControllerImpl implements InternalController {

    private final ResultQueryService queries;
    private final JobCommandService commands;
    private final TypeRegistry types;
    private final ResultMapper mapper;

    public InternalControllerImpl(ResultQueryService queries, JobCommandService commands, TypeRegistry types,
                                  ResultMapper mapper) {
        this.queries = queries;
        this.commands = commands;
        this.types = types;
        this.mapper = mapper;
    }

    @Override
    public ResultRs result(UUID id) {
        return mapper.convert(queries.result(id));
    }

    @Override
    public ResponseEntity<Void> retype(UUID id, RetypeRq rq) {
        if (types.get(rq.type()).isEmpty()) {
            throw DomainException.badRequest("TYPE_UNKNOWN", "Нет такого вида документа", "Выберите вид из списка");
        }
        commands.retype(id, rq.type());
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> reprocess(UUID id) {
        commands.reprocess(id);
        return ResponseEntity.accepted().build();
    }
}
