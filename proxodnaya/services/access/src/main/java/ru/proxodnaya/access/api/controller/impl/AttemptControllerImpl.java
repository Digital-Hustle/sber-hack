package ru.proxodnaya.access.api.controller.impl;

import java.time.Instant;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.AttemptController;
import ru.proxodnaya.access.api.dto.response.AttemptPageRs;
import ru.proxodnaya.access.api.mapper.AttemptApiMapper;
import ru.proxodnaya.access.features.pass.query.model.AttemptFilter;
import ru.proxodnaya.access.features.pass.query.service.AttemptQueryService;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Реализация журнала проходов.
 */
@RestController
public class AttemptControllerImpl implements AttemptController {

    private final AttemptQueryService attempts;
    private final AttemptApiMapper mapper;

    public AttemptControllerImpl(AttemptQueryService attempts, AttemptApiMapper mapper) {
        this.attempts = attempts;
        this.mapper = mapper;
    }

    @Override
    public AttemptPageRs list(Instant from, Instant until, String checkpoint, String worker, Decision decision, ReasonCode reason,
                              AttemptSource source, Direction direction, String cursor, Integer limit) {
        AttemptFilter filter = new AttemptFilter(blankToNull(worker), blankToNull(checkpoint), decision, reason, source, direction,
                from, until);
        return mapper.convert(attempts.page(filter, cursor, limit));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
