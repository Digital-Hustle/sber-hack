package ru.proxodnaya.access.api.controller.impl;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.JournalController;
import ru.proxodnaya.access.api.dto.request.AttemptFilterRq;
import ru.proxodnaya.access.api.dto.response.AttemptRs;
import ru.proxodnaya.access.api.dto.response.AttemptsRs;
import ru.proxodnaya.access.features.pass.query.service.PassQueryService;
import ru.proxodnaya.access.jooq.tables.records.PassAttemptsRecord;

@RestController
public class JournalControllerImpl implements JournalController {

    private final PassQueryService queryService;

    public JournalControllerImpl(PassQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public AttemptsRs list(AttemptFilterRq filter) {
        long total = queryService.countAttempts(
                filter.getWorkerId(), filter.getCheckpoint(), filter.getDecision(),
                filter.getCode(), filter.getSource(), filter.getFrom(), filter.getTo());
        List<PassAttemptsRecord> records = queryService.findAttempts(
                filter.getWorkerId(), filter.getCheckpoint(), filter.getDecision(),
                filter.getCode(), filter.getSource(), filter.getFrom(), filter.getTo(),
                filter.getCursor(), filter.getLimit());
        List<AttemptRs> attempts = records.stream().map(JournalControllerImpl::toRs).toList();
        String nextCursor = records.size() == filter.getLimit() && !records.isEmpty()
                ? records.get(records.size() - 1).getTs().toInstant().toString()
                : null;
        return new AttemptsRs(attempts, nextCursor, total);
    }

    @Override
    public AttemptsRs workerAttempts(String workerId, int limit) {
        long total = queryService.countAttempts(workerId, null, null, null, null, null, null);
        List<PassAttemptsRecord> records = queryService.findAttempts(
                workerId, null, null, null, null, null, null, null, limit);
        List<AttemptRs> attempts = records.stream().map(JournalControllerImpl::toRs).toList();
        return new AttemptsRs(attempts, null, total);
    }

    private static AttemptRs toRs(PassAttemptsRecord r) {
        return new AttemptRs(
                r.getId(),
                r.getTs().toInstant(),
                r.getCheckpointId(),
                r.getDirection(),
                r.getClaimedWorkerId(),
                null,
                r.getDecision(),
                r.getReasonCode(),
                r.getFaceScore(),
                r.getSource()
        );
    }
}
