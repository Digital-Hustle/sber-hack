package ru.documents.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Проверка JWT Keycloak: подпись по JWKS из внутренней сети, issuer — публичный адрес,
 * роли realm_access.roles → ROLE_*.
 */
public final class KeycloakJwt {

    private KeycloakJwt() {
    }

    /**
     * Декодер с проверкой issuer.
     *
     * @param jwkSetUri адрес JWKS (внутренний)
     * @param issuer    ожидаемый iss (публичный)
     * @return декодер
     */
    public static JwtDecoder decoder(String jwkSetUri, String issuer) {
        NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> v = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), KeycloakJwt::notEmptySubject);
        d.setJwtValidator(v);
        return d;
    }

    private static OAuth2TokenValidatorResult notEmptySubject(Jwt jwt) {
        return jwt.getSubject() == null
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "no sub", null))
                : OAuth2TokenValidatorResult.success();
    }

    /**
     * Конвертер ролей Keycloak.
     *
     * @return конвертер
     */
    public static JwtAuthenticationConverter converter() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(KeycloakJwt::roles);
        c.setPrincipalClaimName("sub");
        return c;
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> roles(Jwt jwt) {
        Object ra = jwt.getClaims().get("realm_access");
        List<String> roles = List.of();
        if (ra instanceof Map<?, ?> m && m.get("roles") instanceof List<?> l) {
            roles = (List<String>) l;
        }
        return Stream.of(roles).flatMap(List::stream).filter(Objects::nonNull)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }

    /**
     * Имя пользователя для журналов.
     *
     * @param jwt токен
     * @return preferred_username или sub
     */
    public static String username(Jwt jwt) {
        String u = jwt.getClaimAsString("preferred_username");
        return u != null ? u : jwt.getSubject();
    }
}
