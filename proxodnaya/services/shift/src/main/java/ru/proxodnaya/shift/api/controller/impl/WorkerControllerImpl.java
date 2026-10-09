package ru.proxodnaya.shift.api.controller.impl;

import java.time.Instant;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.shift.api.controller.WorkerController;
import ru.proxodnaya.shift.api.dto.response.ShiftPageRs;
import ru.proxodnaya.shift.api.dto.response.WorkerMeRs;
import ru.proxodnaya.shift.api.mapper.ShiftApiMapper;
import ru.proxodnaya.shift.features.shift.model.ShiftFilter;
import ru.proxodnaya.shift.features.shift.query.service.ShiftQueryService;
import ru.proxodnaya.shift.features.worker.application.WorkerStatusService;
import ru.proxodnaya.shift.shared.Period;

/**
 * Реализация API телефона рабочего.
 */
@RestController
public class WorkerControllerImpl implements WorkerController {

    private final WorkerStatusService status;
    private final ShiftQueryService shifts;
    private final ShiftApiMapper mapper;

    public WorkerControllerImpl(WorkerStatusService status, ShiftQueryService shifts, ShiftApiMapper mapper) {
        this.status = status;
        this.shifts = shifts;
        this.mapper = mapper;
    }

    @Override
    public WorkerMeRs me(Jwt token) {
        return mapper.convert(status.status(token.getSubject()));
    }

    @Override
    public ShiftPageRs shifts(Jwt token, Instant from, Instant until, String cursor, Integer limit) {
        return mapper.convertShifts(shifts.page(new ShiftFilter(token.getSubject(), Period.of(from, until)), cursor, limit));
    }
}
