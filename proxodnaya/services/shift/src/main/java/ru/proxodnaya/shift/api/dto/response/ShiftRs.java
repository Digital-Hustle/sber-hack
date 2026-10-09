package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Смена.
 *
 * @param id         id
 * @param workerId   рабочий
 * @param start      начало
 * @param end        конец
 * @param templateId шаблон или {@code null}
 */
public record ShiftRs(UUID id, String workerId, Instant start, Instant end, @Schema(nullable = true) UUID templateId) {
}
