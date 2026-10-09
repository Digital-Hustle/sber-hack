package ru.documents.review.features.workplace.model;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import ru.documents.common.security.KeycloakJwt;

/**
 * Пользователь из JWT.
 *
 * @param username логин
 * @param roles    роли без префикса ROLE_
 */
public record Actor(String username, Set<String> roles) {

    /**
     * Из аутентификации Spring Security.
     *
     * @param auth аутентификация
     * @return пользователь
     */
    public static Actor of(Authentication auth) {
        String name = auth.getPrincipal() instanceof Jwt jwt ? KeycloakJwt.username(jwt) : auth.getName();
        Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .map(r -> r.startsWith("ROLE_") ? r.substring("ROLE_".length()) : r).collect(Collectors.toSet());
        return new Actor(name, roles);
    }

    /** Старший оператор. */
    public boolean senior() {
        return roles.contains("SENIOR_OPERATOR");
    }

    /** Только аудитор (без ролей оператора) — видит маски вместо ПДн. */
    public boolean auditorOnly() {
        return roles.contains("AUDITOR") && !roles.contains("OPERATOR") && !senior();
    }
}
