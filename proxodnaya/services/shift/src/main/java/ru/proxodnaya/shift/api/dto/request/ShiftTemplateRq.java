package ru.proxodnaya.shift.api.dto.request;

import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Шаблон смены.
 *
 * @param name  название
 * @param start начало, HH:mm
 * @param end   конец; не позже начала — ночная смена
 */
public record ShiftTemplateRq(
        @NotBlank @Size(max = ShiftTemplateRq.MAX_NAME) String name,
        @NotNull @Schema(type = "string", example = "20:00") LocalTime start,
        @NotNull @Schema(type = "string", example = "08:00") LocalTime end
) {

    static final int MAX_NAME = 100;
}
