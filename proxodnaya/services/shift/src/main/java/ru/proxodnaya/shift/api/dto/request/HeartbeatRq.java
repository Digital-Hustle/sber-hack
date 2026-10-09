package ru.proxodnaya.shift.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Heartbeat телефона: раз в 60 с, пока человек на объекте.
 *
 * @param lat      широта
 * @param lon      долгота
 * @param accuracy точность, м
 */
public record HeartbeatRq(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
        @Schema(nullable = true) @PositiveOrZero Double accuracy
) {
}
