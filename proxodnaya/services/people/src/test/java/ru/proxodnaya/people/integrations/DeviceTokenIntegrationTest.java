package ru.proxodnaya.people.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;

import ru.proxodnaya.people.config.AbstractIntegrationTest;

/**
 * Device-токен телефона (PPL-05): challenge → подпись ключом из активации → JWT people, проверяемый по JWKS.
 */
class DeviceTokenIntegrationTest extends AbstractIntegrationTest {

    private static final String WORKERS = "/api/v1/workers";
    private static final String CHALLENGE = "/api/v1/auth/device/challenge";
    private static final String TOKEN = "/api/v1/auth/device/token";

    @Test
    void signedChallenge_givesWorkerToken_verifiableByJwks_andChallengeIsOneTime() throws Exception {
        // given
        String workerId = createWorker();
        ECKey key = new ECKeyGenerator(Curve.P_256).generate();
        String deviceId = activate(workerId, key);
        String challenge = challenge(deviceId);
        String body = tokenRq(deviceId, challenge, sign(key, "auth|" + deviceId + "|" + challenge));

        // when
        JsonNode issued = objectMapper.readTree(mockMvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString());

        // then
        SignedJWT token = SignedJWT.parse(issued.path("accessToken").asText());
        String jwks = mockMvc.perform(get("/internal/people/jwks")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ECKey publicKey = JWKSet.parse(jwks).getKeys().getFirst().toECKey();
        assertThat(publicKey.isPrivate()).isFalse();
        assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(token.verify(new ECDSAVerifier(publicKey))).isTrue();
        assertThat(token.getJWTClaimsSet().getSubject()).isEqualTo(workerId);
        assertThat(token.getJWTClaimsSet().getIssuer()).isEqualTo("https://gate.test/people");
        assertThat(token.getJWTClaimsSet().getAudience()).contains("access", "shift");
        assertThat(token.getJWTClaimsSet().getStringClaim("did")).isEqualTo(deviceId);
        assertThat(token.getJWTClaimsSet().getJSONObjectClaim("realm_access").get("roles")).isEqualTo(List.of("WORKER"));
        assertThat(issued.path("expiresAt").asLong()).isEqualTo(token.getJWTClaimsSet().getExpirationTime().getTime());

        mockMvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHALLENGE_INVALID"));
    }

    @Test
    void rejects_foreignKey_unknownDevice_revokedDevice_blockedWorker() throws Exception {
        // given
        String workerId = createWorker();
        ECKey key = new ECKeyGenerator(Curve.P_256).generate();
        String deviceId = activate(workerId, key);
        ECKey stranger = new ECKeyGenerator(Curve.P_256).generate();

        // when / then — чужой ключ
        String first = challenge(deviceId);
        mockMvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRq(deviceId, first, sign(stranger, "auth|" + deviceId + "|" + first))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEVICE_MISMATCH"));

        // неизвестное устройство: challenge выдаётся (не раскрываем, есть ли id), токен — нет
        String ghost = "ghost-" + UUID.randomUUID().toString().substring(0, 8);
        String ghostChallenge = challenge(ghost);
        mockMvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRq(ghost, ghostChallenge, sign(key, "auth|" + ghost + "|" + ghostChallenge))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEVICE_UNKNOWN"));

        // challenge другого устройства не подходит
        mockMvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRq(deviceId, challenge(ghost), sign(key, "auth|" + deviceId + "|x"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHALLENGE_INVALID"));

        // заблокированный рабочий
        mockMvc.perform(as("ADMIN", post(WORKERS + "/" + workerId + "/block"))).andExpect(status().isOk());
        String blocked = challenge(deviceId);
        mockMvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRq(deviceId, blocked, sign(key, "auth|" + deviceId + "|" + blocked))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORKER_BLOCKED"));

        // отвязанный телефон
        mockMvc.perform(as("ADMIN", delete(WORKERS + "/" + workerId + "/devices/" + deviceId))).andExpect(status().isNoContent());
        String revoked = challenge(deviceId);
        mockMvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRq(deviceId, revoked, sign(key, "auth|" + deviceId + "|" + revoked))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEVICE_REVOKED"));
    }

    private String challenge(String deviceId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(CHALLENGE).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deviceId", deviceId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").isNumber())
                .andReturn().getResponse().getContentAsString()).path("challenge").asText();
    }

    private String tokenRq(String deviceId, String challenge, String signature) throws Exception {
        return objectMapper.writeValueAsString(Map.of("deviceId", deviceId, "challenge", challenge, "signature", signature));
    }

    /** Как WebCrypto: ECDSA P-256/SHA-256, подпись r||s (P1363), base64url. */
    private static String sign(ECKey key, String payload) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(key.toECPrivateKey());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private String activate(String workerId, ECKey key) throws Exception {
        String code = objectMapper.readTree(mockMvc.perform(as("ADMIN", post(WORKERS + "/" + workerId + "/invite")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("inviteCode").asText();
        String deviceId = "tok-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/auth/worker/activate").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("inviteCode", code, "deviceId", deviceId,
                                "publicKeyJwk", key.toPublicJWK().toJSONObject()))))
                .andExpect(status().isOk());
        return deviceId;
    }

    private String createWorker() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("fullName", "Токен Тест", "position", "Монтажник", "contractor", "Дельта"));
        return objectMapper.readTree(mockMvc.perform(as("ADMIN", post(WORKERS).content(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
    }

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON);
    }
}
