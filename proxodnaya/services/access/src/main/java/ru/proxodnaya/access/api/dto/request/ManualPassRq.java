package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ручной проход охранника (FR-14).
 *
 * @param workerId    рабочий, которого пропускают
 * @param direction   IN | OUT
 * @param checkpointId проходная
 * @param reason      причина ручного прохода (обязательная)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualPassRq {

    @NotBlank
    private String workerId;

    @NotNull
    private String direction;

    @NotBlank
    private String checkpointId;

    @NotBlank
    private String reason;
}
