package ru.proxodnaya.people.features.token.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Выпуск JWT people (ES256): device-токены телефонов и сессии персонала. Ключи проверки — {@link #jwks()}.
 */
public interface TokenIssuer {

    /**
     * Подписанный токен.
     *
     * @param subject {@code sub}
     * @param roles   роли в {@code realm_access.roles} — как у Keycloak, чтобы сервисы читали их одинаково
     * @param ttl     срок жизни
     * @param claims  дополнительные claims
     * @return токен и момент истечения
     */
    Issued issue(String subject, List<String> roles, Duration ttl, Map<String, Object> claims);

    /**
     * Публичные ключи (содержимое {@code keys} JWKS).
     *
     * @return JWK без закрытой части
     */
    List<Map<String, Object>> publicKeys();

    /**
     * Выпущенный токен.
     *
     * @param token     JWT
     * @param expiresAt истекает, Unix ms
     */
    record Issued(String token, long expiresAt) {
    }
}
