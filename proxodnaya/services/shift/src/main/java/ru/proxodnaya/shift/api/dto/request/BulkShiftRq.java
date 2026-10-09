package ru.proxodnaya.shift.api.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Массовое назначение смен бригаде по недельному графику.
 *
 * @param workerIds рабочие (до 200)
 * @param from      первая дата
 * @param to        последняя дата, включительно (период до 62 дней)
 * @param days      время по дням недели
 */
public record BulkShiftRq(
        @NotEmpty @Size(max = BulkShiftRq.MAX_WORKERS) List<@NotBlank @Size(max = ShiftRq.MAX_ID) String> workerIds,
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        @NotEmpty @Size(max = BulkShiftRq.DAYS_IN_WEEK) List<@NotNull @Valid WeekdayRq> days
) {

    static final int MAX_WORKERS = 200;
    static final int DAYS_IN_WEEK = 7;
}
