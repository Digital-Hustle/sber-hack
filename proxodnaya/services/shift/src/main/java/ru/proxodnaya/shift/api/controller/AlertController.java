package ru.proxodnaya.shift.api.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import ru.proxodnaya.shift.api.constants.PathConstants;
import ru.proxodnaya.shift.api.dto.response.AlertListRs;
import ru.proxodnaya.shift.api.dto.response.AlertRs;

/**
 * Оповещения B-01/B-05: лента и приём.
 */
@Tag(name = "Alerts", description = "Оповещения")
@RequestMapping(PathConstants.ALERTS)
public interface AlertController {

    /** Больше за раз не отдаём. */
    int MAX_LIMIT = 200;

    /**
     * Последние оповещения.
     *
     * @param status {@code open} — не принятые, {@code all} — все
     * @param limit  1..200
     * @return новые сверху
     */
    @Operation(summary = "Оповещения")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    AlertListRs list(@RequestParam(defaultValue = "open") @Pattern(regexp = "open|all") String status,
                     @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_LIMIT) int limit);

    /**
     * Принять оповещение; повторный приём ничего не меняет.
     *
     * @param id    оповещение
     * @param token кто принял
     * @return оповещение
     */
    @Operation(summary = "Принять оповещение")
    @PostMapping(PathConstants.ACK)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    AlertRs ack(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt token);
}
