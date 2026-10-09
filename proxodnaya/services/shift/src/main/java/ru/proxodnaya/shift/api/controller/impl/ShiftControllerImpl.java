package ru.proxodnaya.shift.api.controller.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.shift.api.controller.ShiftController;
import ru.proxodnaya.shift.api.dto.request.BulkShiftRq;
import ru.proxodnaya.shift.api.dto.request.ShiftRq;
import ru.proxodnaya.shift.api.dto.response.BulkShiftRs;
import ru.proxodnaya.shift.api.dto.response.ShiftPageRs;
import ru.proxodnaya.shift.api.dto.response.ShiftRs;
import ru.proxodnaya.shift.api.mapper.ShiftApiMapper;
import ru.proxodnaya.shift.features.shift.command.service.ShiftCommandService;
import ru.proxodnaya.shift.features.shift.model.ShiftFilter;
import ru.proxodnaya.shift.features.shift.query.service.ShiftQueryService;
import ru.proxodnaya.shift.shared.Period;

/**
 * Реализация API смен.
 */
@RestController
public class ShiftControllerImpl implements ShiftController {

    private final ShiftQueryService query;
    private final ShiftCommandService command;
    private final ShiftApiMapper mapper;

    public ShiftControllerImpl(ShiftQueryService query, ShiftCommandService command, ShiftApiMapper mapper) {
        this.query = query;
        this.command = command;
        this.mapper = mapper;
    }

    @Override
    public ShiftPageRs list(Instant from, Instant until, String worker, String cursor, Integer limit) {
        return mapper.convertShifts(query.page(new ShiftFilter(worker, Period.of(from, until)), cursor, limit));
    }

    @Override
    public ShiftRs create(Jwt token, ShiftRq request) {
        return mapper.convert(command.create(mapper.convert(request), token.getSubject()));
    }

    @Override
    public BulkShiftRs bulk(Jwt token, BulkShiftRq request) {
        return mapper.convert(command.bulk(request.workerIds().stream().map(String::strip).toList(), request.from(), request.to(),
                mapper.convert(request.days()), token.getSubject()));
    }

    @Override
    public void delete(UUID id) {
        command.delete(id);
    }
}
