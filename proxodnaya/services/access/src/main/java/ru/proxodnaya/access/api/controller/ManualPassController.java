package ru.proxodnaya.access.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.request.ManualPassRq;
import ru.proxodnaya.access.api.dto.response.DecisionRs;

/**
 * Ручной проход охранника на киоске (FR-14).
 */
@Tag(name = "Kiosk", description = "Скан QR, челлендж живости, решение о проходе")
@RequestMapping(PathConstants.KIOSK_MANUAL)
public interface ManualPassController {

    /**
     * Пропустить вручную с причиной.
     *
     * @param token   токен охранника ({@code sub} — кто пропустил)
     * @param request рабочий, проходная, причина
     * @return {@code MANUAL / MANUAL_GUARD}, отказ {@code WORKER_BLOCKED} или {@code SYSTEM_ERROR}
     */
    @Operation(summary = "Ручной проход охранника с причиной")
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('GUARD')")
    DecisionRs pass(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @Valid @RequestBody ManualPassRq request);
}
