package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.proxodnaya.access.features.qr.command.service.EcdsaVerifier;
import ru.proxodnaya.access.features.qr.command.service.PublicKeyCodec;
import ru.proxodnaya.access.support.TestKeys;

class QrCryptoTest {

    private static final String MESSAGE = "v1|w1|d1|59000000";

    private final EcdsaVerifier verifier = new EcdsaVerifier();
    private final PublicKeyCodec codec = new PublicKeyCodec(new ObjectMapper());
    private final TestKeys keys = TestKeys.generate();

    @Test
    void verify_webCryptoP1363Signature_accepted() {
        // given
        byte[] sig = keys.signP1363(MESSAGE);

        // when / then
        assertThat(sig).hasSize(64);
        assertThat(verifier.verify(keys.spki(), bytes(MESSAGE), sig)).isTrue();
    }

    @Test
    void verify_derSignature_accepted() {
        // when / then
        assertThat(verifier.verify(keys.spki(), bytes(MESSAGE), keys.signDer(MESSAGE))).isTrue();
    }

    @Test
    void verify_otherMessageOrKey_rejected() {
        // given
        byte[] sig = keys.signP1363(MESSAGE);

        // when / then
        assertThat(verifier.verify(keys.spki(), bytes("v1|w2|d1|59000000"), sig)).isFalse();
        assertThat(verifier.verify(TestKeys.generate().spki(), bytes(MESSAGE), sig)).isFalse();
        assertThat(verifier.verify(new byte[] {1, 2, 3}, bytes(MESSAGE), sig)).isFalse();
    }

    @Test
    void toSpki_rawJwkAndSpki_giveSameKey() {
        // when
        byte[] fromRaw = codec.toSpki(keys.rawB64u());
        byte[] fromJwk = codec.toSpki(keys.jwk());
        byte[] fromSpki = codec.toSpki(Base64.getEncoder().encodeToString(keys.spki()));

        // then
        assertThat(fromRaw).isEqualTo(keys.spki());
        assertThat(fromJwk).isEqualTo(keys.spki());
        assertThat(fromSpki).isEqualTo(keys.spki());
    }

    @Test
    void toSpki_garbageOrWrongCurve_throws() {
        // when / then
        assertThatThrownBy(() -> codec.toSpki("AAEC")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.toSpki("{\"kty\":\"EC\",\"crv\":\"P-384\",\"x\":\"AA\",\"y\":\"AA\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.toSpki(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
