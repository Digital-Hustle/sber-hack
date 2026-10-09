package ru.proxodnaya.access.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.request.SimulationRq;
import ru.proxodnaya.access.api.dto.response.SimulationRs;

/**
 * Демо-история проходов (FR-34, ADR-007).
 */
@Tag(name = "Simulator", description = "Демо-история проходов с пометкой SIMULATOR")
@RequestMapping(PathConstants.SIMULATOR_RUNS)
public interface SimulatorController {

    /**
     * Сгенерировать историю: журнал и {@code access.pass.v1} с {@code source = SIMULATOR}, присутствие не меняется.
     *
     * @param request проходная, рабочие, дни
     * @return сколько записано
     */
    @Operation(summary = "Сгенерировать демо-историю проходов")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    SimulationRs run(@Valid @RequestBody SimulationRq request);
}
