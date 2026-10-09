package ru.proxodnaya.access.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.request.SimulatePassRq;
import ru.proxodnaya.access.api.dto.response.DecisionRs;

/**
 * Симулятор проходов (FR-34). Тот же DecisionEngine, source=SIMULATOR.
 * Доступ: ADMIN и MANAGER. В интерфейсе помечается как симуляция.
 */
@Tag(name = "Simulator", description = "Симулятор проходов через тот же движок")
@RequestMapping(PathConstants.SIMULATE)
public interface SimulatorController {

    /**
     * Симулировать проход.
     *
     * @param request данные (workerId, direction, checkpointId)
     * @return решение
     */
    @Operation(summary = "Симулировать проход (source=SIMULATOR)")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    DecisionRs simulate(@Valid @RequestBody SimulatePassRq request);
}
