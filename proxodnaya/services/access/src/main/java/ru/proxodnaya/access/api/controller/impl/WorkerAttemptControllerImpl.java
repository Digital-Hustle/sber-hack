package ru.proxodnaya.access.api.controller.impl;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.WorkerAttemptController;
import ru.proxodnaya.access.api.dto.response.AttemptPageRs;
import ru.proxodnaya.access.api.mapper.AttemptApiMapper;
import ru.proxodnaya.access.features.pass.query.model.AttemptFilter;
import ru.proxodnaya.access.features.pass.query.service.AttemptQueryService;

/**
 * Реализация истории проходов рабочего.
 */
@RestController
public class WorkerAttemptControllerImpl implements WorkerAttemptController {

    private final AttemptQueryService attempts;
    private final AttemptApiMapper mapper;

    public WorkerAttemptControllerImpl(AttemptQueryService attempts, AttemptApiMapper mapper) {
        this.attempts = attempts;
        this.mapper = mapper;
    }

    @Override
    public AttemptPageRs mine(Jwt token, String cursor, Integer limit) {
        return mapper.convert(attempts.page(AttemptFilter.ofWorker(token.getSubject()), cursor, limit));
    }
}
