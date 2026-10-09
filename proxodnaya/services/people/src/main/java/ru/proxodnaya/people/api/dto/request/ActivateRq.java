package ru.proxodnaya.people.api.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Активация телефона.
 *
 * @param inviteCode   код инвайта
 * @param deviceId     id устройства (без точек: он едет в QR через точку)
 * @param publicKeyJwk публичный ключ ECDSA P-256 (WebCrypto exportKey jwk)
 */
public record ActivateRq(
        @NotBlank(message = "Введите код приглашения") @Size(max = MAX_CODE, message = "Слишком длинный код") String inviteCode,
        @NotBlank(message = "Нет id устройства")
        @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "id устройства: 8–64 символа A-Z, a-z, 0-9, _ или -")
        String deviceId,
        @NotEmpty(message = "Нет публичного ключа") Map<String, Object> publicKeyJwk) {

    /** Код с запасом на пробелы и дефисы при вводе. */
    public static final int MAX_CODE = 16;
}
