package ru.proxodnaya.people.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Доступ тестов к package-private конвертеру ролей.
 */
public final class SecurityConfigAccess {

    private SecurityConfigAccess() {
    }

    /**
     * Конвертирует JWT так же, как resource server.
     *
     * @param jwt токен
     * @return аутентификация
     */
    public static AbstractAuthenticationToken convert(Jwt jwt) {
        return SecurityConfig.realmRoles().convert(jwt);
    }
}
