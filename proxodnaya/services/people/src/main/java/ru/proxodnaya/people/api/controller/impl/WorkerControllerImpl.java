package ru.proxodnaya.people.api.controller.impl;

import java.util.UUID;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.people.api.controller.WorkerController;
import ru.proxodnaya.people.api.dto.request.CreateWorkerRq;
import ru.proxodnaya.people.api.dto.request.UpdateWorkerRq;
import ru.proxodnaya.people.api.dto.response.WorkerPageRs;
import ru.proxodnaya.people.api.dto.response.WorkerRs;
import ru.proxodnaya.people.api.mapper.WorkerApiMapper;
import ru.proxodnaya.people.features.worker.command.service.WorkerCommandService;
import ru.proxodnaya.people.features.worker.query.service.WorkerQueryService;

/**
 * Реализация API рабочих.
 */
@RestController
public class WorkerControllerImpl implements WorkerController {

    private final WorkerCommandService commands;
    private final WorkerQueryService queries;
    private final WorkerApiMapper mapper;

    public WorkerControllerImpl(WorkerCommandService commands, WorkerQueryService queries, WorkerApiMapper mapper) {
        this.commands = commands;
        this.queries = queries;
        this.mapper = mapper;
    }

    @Override
    public WorkerPageRs list(String query, String filter, String cursor, Integer limit) {
        return mapper.toRs(queries.page(query, filter, cursor, limit));
    }

    @Override
    public WorkerRs get(UUID id) {
        return mapper.toRs(queries.get(id));
    }

    @Override
    public WorkerRs create(CreateWorkerRq request) {
        return mapper.toRs(queries.get(commands.create(mapper.toDraft(request)).id()));
    }

    @Override
    public WorkerRs update(UUID id, UpdateWorkerRq request) {
        commands.update(id, mapper.toDraft(request));
        return mapper.toRs(queries.get(id));
    }

    @Override
    public WorkerRs block(UUID id) {
        commands.block(id);
        return mapper.toRs(queries.get(id));
    }

    @Override
    public WorkerRs unblock(UUID id) {
        commands.unblock(id);
        return mapper.toRs(queries.get(id));
    }

    @Override
    public void delete(UUID id) {
        commands.delete(id);
    }
}
