package ru.proxodnaya.access.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.request.FramesRq;
import ru.proxodnaya.access.api.dto.request.ScanRq;
import ru.proxodnaya.access.api.dto.response.DecisionRs;
import ru.proxodnaya.access.api.dto.response.ScanRs;

/**
 * Киоск проходной: скан QR → челлендж живости → кадры → решение.
 */
@Tag(name = "Kiosk", description = "Скан QR, челлендж живости, решение о проходе")
@RequestMapping(PathConstants.KIOSK_ATTEMPTS)
public interface KioskController {

    /**
     * Скан QR.
     *
     * @param request   QR и проходная
     * @param principal терминал из подписи ({@code KioskPrincipal}, проходная — его) или охранник с токеном
     * @return решение (отказ) или челлендж
     */
    @Operation(summary = "Скан QR: сразу отказ или челлендж живости")
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('KIOSK', 'GUARD')")
    ScanRs scan(@Valid @RequestBody ScanRq request, @Parameter(hidden = true) @AuthenticationPrincipal Object principal);

    /**
     * Кадры челленджа.
     *
     * @param token токен из ответа на скан
     * @param request    кадры
     * @return решение
     */
    @Operation(summary = "Кадры челленджа → решение о проходе")
    @PostMapping(PathConstants.FRAMES)
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('KIOSK', 'GUARD')")
    DecisionRs submitFrames(@PathVariable UUID token, @Valid @RequestBody FramesRq request);
}
