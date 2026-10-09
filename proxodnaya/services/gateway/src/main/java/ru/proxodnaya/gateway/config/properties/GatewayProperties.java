package ru.proxodnaya.gateway.config.properties;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Настройки gateway ({@code gateway.*}).
 *
 * @param issuers   доверенные издатели JWT: Keycloak и (после PPL-05) device-токены people
 * @param cors      разрешённые origin кабинета и PWA
 * @param rateLimit лимит запросов на чувствительные пути
 */
@Validated
@ConfigurationProperties("gateway")
public record GatewayProperties(@NotEmpty List<@Valid Issuer> issuers, @NotNull List<String> cors,
                                @Valid @NotNull RateLimit rateLimit) {

    /**
     * Издатель JWT.
     *
     * @param issuer    значение {@code iss}
     * @param jwkSetUri откуда брать ключи подписи
     */
    public record Issuer(@NotBlank String issuer, @NotBlank String jwkSetUri) {
    }

    /**
     * Лимит запросов с одного адреса (token bucket в памяти: при рестарте сбрасывается — допустимо).
     *
     * @param paths    шаблоны путей ({@code /people/api/v1/auth/**})
     * @param capacity запросов в окне
     * @param window   окно пополнения
     */
    public record RateLimit(@NotNull List<String> paths, @Positive int capacity, @NotNull Duration window) {
    }
}
