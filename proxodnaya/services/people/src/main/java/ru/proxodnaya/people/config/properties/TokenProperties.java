package ru.proxodnaya.people.config.properties;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Токены, которые выпускает people ({@code people.tokens.*}, ADR-011, ADR-040).
 *
 * @param issuer         значение {@code iss}; его же принимают gateway и сервисы
 * @param signingKey     закрытый ключ ECDSA P-256, PKCS#8 DER в base64 ({@code scripts/gen-env.sh})
 * @param audiences      {@code aud} — сервисы, которым адресован токен
 * @param deviceTtl      срок device-токена телефона
 * @param challengeTtl   срок challenge для device-токена
 */
@Validated
@ConfigurationProperties("people.tokens")
public record TokenProperties(@NotBlank String issuer, @NotBlank String signingKey, @NotEmpty List<String> audiences,
                              @NotNull Duration deviceTtl, @NotNull Duration challengeTtl) {
}
