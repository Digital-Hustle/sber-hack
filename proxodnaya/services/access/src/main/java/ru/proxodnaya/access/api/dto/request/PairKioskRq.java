package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Привязка терминала по коду с его экрана.
 *
 * @param pairCode      код сопряжения (6 символов)
 * @param name          название в кабинете
 * @param checkpointId  проходная
 * @param mode          своя логика прохода или {@code null} — общая
 * @param offlinePolicy своя политика без связи или {@code null} — общая
 */
public record PairKioskRq(
        @NotBlank @Size(max = PairKioskRq.MAX_CODE) String pairCode,
        @NotBlank @Size(max = PairKioskRq.MAX_NAME) String name,
        @NotBlank @Size(max = PairKioskRq.MAX_ID) String checkpointId,
        @Pattern(regexp = UpdateKioskRq.MODES, message = UpdateKioskRq.MODE_MESSAGE) String mode,
        @Pattern(regexp = UpdateKioskRq.POLICIES, message = UpdateKioskRq.POLICY_MESSAGE) String offlinePolicy
) {
    static final int MAX_CODE = 16;
    static final int MAX_NAME = 80;
    static final int MAX_ID = 64;
}
