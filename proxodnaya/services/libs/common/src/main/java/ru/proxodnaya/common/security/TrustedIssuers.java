package ru.proxodnaya.common.security;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Два доверенных издателя JWT (ADR-011, ADR-040): Keycloak — сервисные аккаунты, и people — телефоны рабочих
 * (device-токен) и персонал (вход по коду из письма). Токен издателя, которого нет в списке, отклоняется.
 *
 * <p>У каждого издателя проверяются подпись (ключи из JWKS во внутренней сети), {@code iss}, срок и {@code aud}
 * сервиса. Роли — из {@code realm_access.roles} у обоих.</p>
 */
public final class TrustedIssuers {

    private static final String KC = "spring.security.oauth2.resourceserver.jwt.";
    private static final String PEOPLE = "proxodnaya.security.people-";
    private static final String AUDIENCE = "aud";

    private TrustedIssuers() {
    }

    /**
     * Резолвер менеджеров аутентификации по {@code iss} токена.
     *
     * @param env       окружение: {@code spring.security.oauth2.resourceserver.jwt.{issuer-uri,jwk-set-uri,audiences}} и
     *                  {@code proxodnaya.security.people-{issuer,jwks-uri}}
     * @param converter JWT → аутентификация с ролями
     * @return резолвер
     */
    public static AuthenticationManagerResolver<HttpServletRequest> resolver(Environment env,
            Converter<Jwt, ? extends AbstractAuthenticationToken> converter) {
        String audience = env.getProperty(KC + "audiences", "");
        Map<String, AuthenticationManager> managers = new LinkedHashMap<>();
        add(managers, env.getProperty(KC + "issuer-uri", ""), env.getProperty(KC + "jwk-set-uri", ""), SignatureAlgorithm.RS256,
                audience, converter);
        add(managers, env.getProperty(PEOPLE + "issuer", ""), env.getProperty(PEOPLE + "jwks-uri", ""), SignatureAlgorithm.ES256,
                audience, converter);
        return new JwtIssuerAuthenticationManagerResolver(managers::get);
    }

    /**
     * Роли из {@code realm_access.roles} → {@code ROLE_*}, пользователь — {@link CurrentUser}.
     *
     * @return конвертер
     */
    public static Converter<Jwt, AbstractAuthenticationToken> realmRoles() {
        return jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            Object roles = realm == null ? null : realm.get("roles");
            Collection<GrantedAuthority> authorities = roles instanceof List<?> list
                    ? list.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList()
                    : List.of();
            return new CurrentUser(jwt, authorities);
        };
    }

    private static void add(Map<String, AuthenticationManager> managers, String issuer, String jwksUri, SignatureAlgorithm algorithm,
                            String audience, Converter<Jwt, ? extends AbstractAuthenticationToken> converter) {
        if (issuer.isBlank() || jwksUri.isBlank()) {
            return;
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).jwsAlgorithm(algorithm).build();
        OAuth2TokenValidator<Jwt> audienceOk = new JwtClaimValidator<List<String>>(AUDIENCE,
                aud -> audience.isBlank() || aud != null && aud.contains(audience));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceOk));
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        managers.put(issuer, provider::authenticate);
    }
}
