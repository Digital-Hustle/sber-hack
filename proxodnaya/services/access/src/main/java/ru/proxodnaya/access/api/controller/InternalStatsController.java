package ru.proxodnaya.access.api.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.response.AccessStatsRs;

/**
 * Внутренний API для assistant (FR-42, read-only, только service accounts).
 * Не маршрутизируется через gateway (gateway блокирует /internal/**).
 */
@Tag(name = "Internal", description = "Внутренний read-only API для assistant")
@RequestMapping(PathConstants.INTERNAL_STATS)
public interface InternalStatsController {

    /**
     * Статистика проходов за период.
     *
     * @param from начало
     * @param to   конец
     * @return агрегаты
     */
    @Operation(summary = "Статистика проходов для assistant")
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_svc:assistant')")
    AccessStatsRs stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    );
}
