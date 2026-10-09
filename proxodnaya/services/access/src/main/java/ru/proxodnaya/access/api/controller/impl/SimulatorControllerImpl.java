package ru.proxodnaya.access.api.controller.impl;

import java.time.Clock;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.SimulatorController;
import ru.proxodnaya.access.api.dto.request.SimulatePassRq;
import ru.proxodnaya.access.api.dto.response.DecisionRs;
import ru.proxodnaya.access.api.mapper.PassApiMapper;
import ru.proxodnaya.access.features.pass.application.PassService;
import ru.proxodnaya.access.shared.Direction;

@RestController
public class SimulatorControllerImpl implements SimulatorController {

    private final PassService passService;
    private final PassApiMapper mapper;
    private final Clock clock;

    public SimulatorControllerImpl(PassService passService, PassApiMapper mapper, Clock clock) {
        this.passService = passService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public DecisionRs simulate(SimulatePassRq request) {
        return mapper.convert(passService.simulatePass(
                request.getWorkerId(),
                Direction.valueOf(request.getDirection()),
                request.getCheckpointId(),
                clock.instant()
        ));
    }
}
