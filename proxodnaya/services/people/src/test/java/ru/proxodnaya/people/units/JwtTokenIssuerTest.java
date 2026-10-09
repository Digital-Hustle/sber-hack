package ru.proxodnaya.people.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.KeyPair;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;

import ru.proxodnaya.people.config.properties.TokenProperties;
import ru.proxodnaya.people.features.token.api.TokenIssuer;
import ru.proxodnaya.people.features.token.command.service.JwtTokenIssuer;

class JwtTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

    @Test
    void derivesPublicKey_fromJavaPkcs8_andSignsVerifiableToken() throws Exception {
        // given — PKCS#8 из Java не содержит открытой точки: её выводим сами
        KeyPair pair = pair();
        TokenProperties props = new TokenProperties("https://gate.test/people",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), List.of("access"), Duration.ofMinutes(10),
                Duration.ofSeconds(60));
        JwtTokenIssuer issuer = new JwtTokenIssuer(props, Clock.fixed(NOW, ZoneOffset.UTC));

        // when
        TokenIssuer.Issued issued = issuer.issue("worker-1", List.of("WORKER"), Duration.ofMinutes(10), Map.of("did", "dev-1"));

        // then
        ECKey published = JWKSet.parse(Map.of("keys", issuer.publicKeys())).getKeys().getFirst().toECKey();
        assertThat(published.toECPublicKey().getW()).isEqualTo(((ECPublicKey) pair.getPublic()).getW());
        SignedJWT token = SignedJWT.parse(issued.token());
        assertThat(token.verify(new ECDSAVerifier(published))).isTrue();
        assertThat(token.getHeader().getKeyID()).isEqualTo(published.getKeyID());
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)).toEpochMilli());
    }

    @Test
    void rejects_garbageKey() {
        // given
        TokenProperties props = new TokenProperties("iss", "bm90LWEta2V5", List.of("access"), Duration.ofMinutes(1),
                Duration.ofSeconds(1));

        // when / then
        assertThatThrownBy(() -> new JwtTokenIssuer(props, Clock.systemUTC())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEVICE_TOKEN_SIGNING_KEY");
    }

    private static KeyPair pair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
