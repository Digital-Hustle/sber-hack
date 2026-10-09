package ru.proxodnaya.people.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Обмен подписанного challenge на device-токен.
 *
 * @param deviceId  id устройства
 * @param challenge challenge из {@code /auth/device/challenge}
 * @param signature ECDSA P-256/SHA-256 над {@code auth|<deviceId>|<challenge>}, base64url (r||s или DER)
 */
public record DeviceTokenRq(
        @NotBlank(message = "Нет id устройства")
        @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "id устройства: 8–64 символа A-Z, a-z, 0-9, _ или -")
        String deviceId,
        @NotBlank(message = "Нет challenge") @Size(max = MAX, message = "Слишком длинный challenge") String challenge,
        @NotBlank(message = "Нет подписи") @Size(max = MAX, message = "Слишком длинная подпись") String signature) {

    /** Предел длины challenge и подписи. */
    public static final int MAX = 256;
}
