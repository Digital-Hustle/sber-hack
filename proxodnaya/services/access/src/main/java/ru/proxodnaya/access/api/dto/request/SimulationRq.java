package ru.proxodnaya.access.api.dto.request;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Прогон симулятора (FR-34): история за прошедшие будни, {@code source = SIMULATOR}.
 *
 * @param checkpointId проходная
 * @param workerIds    рабочие (id из people)
 * @param days         за сколько прошедших дней (сегодня не трогается); лимит — {@code SIMULATOR_MAX_DAYS}
 * @param denyRate     доля дней с отказом перед входом, 0..0,5; по умолчанию 0,1
 * @param seed         зерно: одинаковое — одинаковая история
 */
public record SimulationRq(
        @NotBlank @Size(max = SimulationRq.MAX_ID) String checkpointId,
        @NotEmpty List<@NotBlank @Size(max = SimulationRq.MAX_ID) String> workerIds,
        int days,
        @DecimalMin("0") @DecimalMax("0.5") Double denyRate,
        Long seed
) {
    static final int MAX_ID = 64;
}
