package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Изменение терминала; {@code null} — поле не меняется.
 *
 * @param name          название
 * @param checkpointId  проходная
 * @param mode          логика прохода
 * @param offlinePolicy политика без связи
 */
public record UpdateKioskRq(
        @Size(min = 1, max = UpdateKioskRq.MAX_NAME) String name,
        @Size(min = 1, max = UpdateKioskRq.MAX_ID) String checkpointId,
        @Pattern(regexp = UpdateKioskRq.MODES, message = UpdateKioskRq.MODE_MESSAGE) String mode,
        @Pattern(regexp = UpdateKioskRq.POLICIES, message = UpdateKioskRq.POLICY_MESSAGE) String offlinePolicy
) {
    static final String MODES = "QR_FACE|FACE_FIRST|QR_ONLY";
    static final String MODE_MESSAGE = "Логика терминала: QR_FACE, FACE_FIRST или QR_ONLY";
    static final String POLICIES = "GUARD|CLOSED|LOCAL";
    static final String POLICY_MESSAGE = "Без связи: GUARD, CLOSED или LOCAL";
    static final int MAX_NAME = 80;
    static final int MAX_ID = 64;
}
