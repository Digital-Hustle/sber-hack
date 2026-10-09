package ru.documents.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/** Проверки токена: issuer, sub, aud (PLT-06). */
class KeycloakJwtTest {

    private static final String ISS = "https://host/auth/realms/documents";

    private final OAuth2TokenValidator<Jwt> v = KeycloakJwt.validator(ISS, List.of("documents-api", "intake"));

    @Test
    void operatorAndOwnServiceAudienceAccepted() {
        assertThat(v.validate(jwt(ISS, List.of("documents-api", "account"))).hasErrors()).isFalse();
        assertThat(v.validate(jwt(ISS, List.of("ocr", "intake"))).hasErrors()).isFalse();
    }

    @Test
    void foreignAudienceOrIssuerRejected() {
        assertThat(v.validate(jwt(ISS, List.of("ocr"))).hasErrors()).isTrue();
        assertThat(v.validate(jwt(ISS, List.of())).hasErrors()).isTrue();
        assertThat(v.validate(jwt("https://evil/realms/documents", List.of("intake"))).hasErrors()).isTrue();
    }

    @Test
    void emptyExpectedListSkipsAudienceCheck() {
        assertThat(KeycloakJwt.validator(ISS, List.of(" ")).validate(jwt(ISS, List.of("x"))).hasErrors()).isFalse();
    }

    private static Jwt jwt(String iss, List<String> aud) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").issuer(iss).subject("u")
                .issuedAt(Instant.now().minusSeconds(10)).expiresAt(Instant.now().plusSeconds(300));
        if (!aud.isEmpty()) {
            b.audience(aud);
        }
        return b.build();
    }
}
