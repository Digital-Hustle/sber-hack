package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Запрос симулятора (FR-34, source=SIMULATOR). Тот же движок, но без QR и без лица.
 *
 * @param workerId    рабочий
 * @param direction    IN | OUT
 * @param checkpointId проходная
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulatePassRq {

    @NotBlank
    private String workerId;

    @NotNull
    private String direction;

    @NotBlank
    private String checkpointId;
}
