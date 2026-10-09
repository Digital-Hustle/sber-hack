package ru.proxodnaya.people.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.people.features.device.command.service.DeviceKeys;

class DeviceKeysTest {

    @Test
    void normalize_publicP256_keepsOnlyPublicMembers() throws Exception {
        // given
        Map<String, Object> jwk = new HashMap<>(new ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toJSONObject());
        jwk.put("ext", true);
        jwk.put("key_ops", java.util.List.of("verify"));

        // when
        ECKey key = DeviceKeys.normalize(jwk);

        // then
        assertThat(key.toJSONObject()).containsOnlyKeys("kty", "crv", "x", "y");
        assertThat(key.isPrivate()).isFalse();
    }

    @Test
    void normalize_privateKey_rejected() throws Exception {
        // given
        Map<String, Object> jwk = new ECKeyGenerator(Curve.P_256).generate().toJSONObject();

        // when / then
        assertThatThrownBy(() -> DeviceKeys.normalize(jwk)).isInstanceOf(DomainException.class);
    }

    @Test
    void normalize_otherCurveOrGarbage_rejected() throws Exception {
        // given
        Map<String, Object> p384 = new ECKeyGenerator(Curve.P_384).generate().toPublicJWK().toJSONObject();
        Map<String, Object> notOnCurve = new HashMap<>(new ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toJSONObject());
        notOnCurve.put("y", notOnCurve.get("x"));

        // when / then
        assertThatThrownBy(() -> DeviceKeys.normalize(p384)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> DeviceKeys.normalize(notOnCurve)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> DeviceKeys.normalize(Map.of("kty", "oct", "k", "AAAA"))).isInstanceOf(DomainException.class);
    }
}
