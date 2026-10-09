package ru.proxodnaya.shift.api.dto.response;

import java.time.LocalTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Шаблон смены.
 *
 * @param id    id
 * @param name  название
 * @param start начало, HH:mm:ss
 * @param end   конец
 */
public record ShiftTemplateRs(UUID id, String name, @Schema(type = "string", example = "08:00:00") LocalTime start,
                              @Schema(type = "string", example = "20:00:00") LocalTime end) {
}
