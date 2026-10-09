package ru.proxodnaya.shift.api.dto.request;

import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Время смены в день недели.
 *
 * @param weekday 0..6, 0 — понедельник
 * @param start   начало, HH:mm (пояс площадки)
 * @param end     конец; не позже начала — на следующий день
 */
public record WeekdayRq(
        @NotNull @Min(0) @Max(WeekdayRq.SUNDAY) Integer weekday,
        @NotNull @Schema(type = "string", example = "08:00") LocalTime start,
        @NotNull @Schema(type = "string", example = "20:00") LocalTime end
) {

    static final int SUNDAY = 6;
}
