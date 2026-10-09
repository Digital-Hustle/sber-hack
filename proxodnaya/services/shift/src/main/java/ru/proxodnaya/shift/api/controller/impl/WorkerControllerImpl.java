package ru.proxodnaya.shift.api.controller.impl;

import java.time.Instant;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.shift.api.controller.WorkerController;
import ru.proxodnaya.shift.api.dto.request.HeartbeatRq;
import ru.proxodnaya.shift.api.dto.response.HeartbeatRs;
import ru.proxodnaya.shift.api.dto.response.ShiftPageRs;
import ru.proxodnaya.shift.api.dto.response.WorkerMeRs;
import ru.proxodnaya.shift.api.mapper.AlertApiMapper;
import ru.proxodnaya.shift.api.mapper.ShiftApiMapper;
import ru.proxodnaya.shift.features.alert.command.service.HeartbeatService;
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
    private final HeartbeatService heartbeats;
    private final AlertApiMapper alertMapper;

    public WorkerControllerImpl(WorkerStatusService status, ShiftQueryService shifts, ShiftApiMapper mapper, HeartbeatService heartbeats,
                                AlertApiMapper alertMapper) {
        this.status = status;
        this.shifts = shifts;
        this.mapper = mapper;
        this.heartbeats = heartbeats;
        this.alertMapper = alertMapper;
    }

    @Override
    public WorkerMeRs me(Jwt token) {
        return mapper.convert(status.status(token.getSubject()));
    }

    @Override
    public ShiftPageRs shifts(Jwt token, Instant from, Instant until, String cursor, Integer limit) {
        return mapper.convertShifts(shifts.page(new ShiftFilter(token.getSubject(), Period.of(from, until)), cursor, limit));
    }

    @Override
    public HeartbeatRs heartbeat(Jwt token, HeartbeatRq request) {
        return alertMapper.convert(heartbeats.record(token.getSubject(), request.lat(), request.lon(), request.accuracy()));
    }
}
