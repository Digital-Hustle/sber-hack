package ru.proxodnaya.access.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.SimulatorController;
import ru.proxodnaya.access.api.dto.request.SimulationRq;
import ru.proxodnaya.access.api.dto.response.SimulationRs;
import ru.proxodnaya.access.features.pass.application.SimulatorService;
import ru.proxodnaya.access.features.pass.model.SimulationRequest;
import ru.proxodnaya.access.features.pass.model.SimulationResult;

/**
 * Реализация симулятора.
 */
@RestController
public class SimulatorControllerImpl implements SimulatorController {

    private static final double DEFAULT_DENY_RATE = 0.1;

    private final SimulatorService simulator;

    public SimulatorControllerImpl(SimulatorService simulator) {
        this.simulator = simulator;
    }

    @Override
    public SimulationRs run(SimulationRq request) {
        SimulationResult result = simulator.run(new SimulationRequest(request.checkpointId().strip(), request.workerIds(),
                request.days(), request.denyRate() == null ? DEFAULT_DENY_RATE : request.denyRate(), request.seed()));
        return new SimulationRs(result.attempts(), result.allowed(), result.denied(), result.firstTs(), result.lastTs());
    }
}
