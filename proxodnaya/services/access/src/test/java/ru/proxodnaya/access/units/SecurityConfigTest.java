package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import ru.proxodnaya.access.config.SecurityConfigAccess;

class SecurityConfigTest {

    @Test
    void realmRoles_keycloakToken_rolesWithPrefix() {
        // given
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("u1")
                .claim("realm_access", Map.of("roles", List.of("GUARD", "ADMIN")))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        // when
        AbstractAuthenticationToken auth = SecurityConfigAccess.convert(jwt);

        // then
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_GUARD", "ROLE_ADMIN");
        assertThat(auth.getName()).isEqualTo("u1");
    }
}
