package ru.proxodnaya.shift.api.dto.request;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Назначение смены.
 *
 * @param workerId   рабочий
 * @param start      начало (ISO-8601)
 * @param end        конец, позже начала, не дольше 24 ч
 * @param templateId шаблон, если смена по шаблону
 */
public record ShiftRq(
        @NotBlank @Size(max = ShiftRq.MAX_ID) String workerId,
        @NotNull Instant start,
        @NotNull Instant end,
        @Schema(nullable = true) UUID templateId
) {

    static final int MAX_ID = 64;
}
