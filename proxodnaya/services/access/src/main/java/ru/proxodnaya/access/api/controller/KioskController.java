package ru.proxodnaya.access.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.access.api.dto.request.CreateAttemptRq;
import ru.proxodnaya.access.api.dto.request.SubmitFramesRq;
import ru.proxodnaya.access.api.dto.response.CreateAttemptRs;
import ru.proxodnaya.access.api.dto.response.PassDecisionRs;

/**
 * API киоска проходной: приём QR → челлендж → кадры → решение.
 */
@Tag(name = "Kiosk", description = "Киоск проходной: приём QR, челлендж живости, решение")
@RequestMapping("/api/v1/kiosk/attempts")
public interface KioskController {

    @Operation(summary = "Создать попытку прохода: QR → челлендж или сразу DENY")
    @PostMapping
    @PreAuthorize("hasRole('GUARD')")
    ResponseEntity<CreateAttemptRs> createAttempt(@Valid @RequestBody CreateAttemptRq request);

    @Operation(summary = "Отправить кадры челленджа → решение")
    @PostMapping("/{attemptId}/frames")
    @PreAuthorize("hasRole('GUARD')")
    ResponseEntity<PassDecisionRs> submitFrames(
            @PathVariable String attemptId,
            @Valid @RequestBody SubmitFramesRq request);
}
