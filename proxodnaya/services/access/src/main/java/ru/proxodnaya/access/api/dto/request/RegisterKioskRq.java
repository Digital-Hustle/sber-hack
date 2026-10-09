package ru.proxodnaya.access.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Первый запуск терминала: публичный ключ (закрытый — неизвлекаемый, остаётся на устройстве).
 *
 * @param publicKeyJwk JWK EC P-256 без {@code d}
 */
public record RegisterKioskRq(@NotNull @Valid Jwk publicKeyJwk) {

    /**
     * Публичный ключ EC.
     *
     * @param kty {@code EC}
     * @param crv {@code P-256}
     * @param x   координата, base64url
     * @param y   координата, base64url
     */
    public record Jwk(@NotBlank @Size(max = Jwk.MAX) String kty, @NotBlank @Size(max = Jwk.MAX) String crv,
                      @NotBlank @Size(max = Jwk.MAX) String x, @NotBlank @Size(max = Jwk.MAX) String y) {
        static final int MAX = 128;
    }
}
