package ru.proxodnaya.people.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import ru.proxodnaya.people.config.AbstractIntegrationTest;
import ru.proxodnaya.people.jooq.Tables;

/**
 * Инвайт → активация телефона → события для access (PPL-03/04).
 */
class DeviceActivationIntegrationTest extends AbstractIntegrationTest {

    private static final String WORKERS = "/api/v1/workers";
    private static final String ACTIVATE = "/api/v1/auth/worker/activate";

    @Autowired
    private DSLContext dsl;

    @Test
    void activate_byInvite_bindsKeyPublishesDeviceBound_andInviteIsOneTime() throws Exception {
        // given
        String workerId = createWorker();
        JsonNode invite = issueInvite(workerId);
        String code = invite.path("inviteCode").asText();
        ECKey key = new ECKeyGenerator(Curve.P_256).generate();
        String deviceId = "dev-" + UUID.randomUUID().toString().substring(0, 8);

        // when / then — без токена: рабочего подтверждает инвайт
        assertThat(invite.path("link").asText()).isEqualTo("https://gate.test/worker/activate?c=" + code);
        mockMvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON).content(activation(code.toLowerCase(), deviceId, key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workerId").value(workerId))
                .andExpect(jsonPath("$.card.fullName").value("Активация Тест"))
                .andExpect(jsonPath("$.serverTime").isNotEmpty());
        mockMvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON).content(activation(code, "other-device-1", key)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));

        JsonNode event = lastEvent(deviceId);
        assertThat(event.path("type").asText()).isEqualTo("DeviceBound");
        assertThat(event.path("payload").path("workerId").asText()).isEqualTo(workerId);
        assertThat(event.path("payload").path("publicKey").path("x").asText()).isEqualTo(key.getX().toString());
        assertThat(event.path("payload").path("publicKey").has("d")).isFalse();
    }

    @Test
    void newPhone_revokesPrevious_andAdminCanListAndRevoke() throws Exception {
        // given
        String workerId = createWorker();
        String first = "phone-one-" + UUID.randomUUID().toString().substring(0, 6);
        String second = "phone-two-" + UUID.randomUUID().toString().substring(0, 6);
        activate(issueInvite(workerId).path("inviteCode").asText(), first);

        // when
        activate(issueInvite(workerId).path("inviteCode").asText(), second);

        // then
        assertThat(lastEvent(first).path("type").asText()).isEqualTo("DeviceRevoked");
        mockMvc.perform(as("SECURITY_OFFICER", get(WORKERS + "/" + workerId + "/devices")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].deviceId").value(second))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[1].status").value("REVOKED"))
                .andExpect(jsonPath("$.items[0].publicKeyJwk").doesNotExist());
        mockMvc.perform(as("MANAGER", delete(WORKERS + "/" + workerId + "/devices/" + second))).andExpect(status().isForbidden());
        mockMvc.perform(as("ADMIN", delete(WORKERS + "/" + workerId + "/devices/" + second))).andExpect(status().isNoContent());
        mockMvc.perform(as("ADMIN", delete(WORKERS + "/" + workerId + "/devices/" + second))).andExpect(status().isNotFound());
        assertThat(lastEvent(second).path("type").asText()).isEqualTo("DeviceRevoked");
    }

    @Test
    void rejects_expiredInvite_privateKey_badDeviceId_boundDevice_blockedWorker() throws Exception {
        // given
        String workerId = createWorker();
        String code = issueInvite(workerId).path("inviteCode").asText();
        ECKey key = new ECKeyGenerator(Curve.P_256).generate();

        // when / then
        mockMvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("inviteCode", code, "deviceId", "device-priv-1",
                                "publicKeyJwk", key.toJSONObject()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON).content(activation(code, "bad.device.id", key)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("deviceId"));

        dsl.update(Tables.INVITES).set(Tables.INVITES.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1))
                .where(Tables.INVITES.WORKER_ID.eq(UUID.fromString(workerId))).execute();
        mockMvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON).content(activation(code, "device-late-1", key)))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("INVITE_EXPIRED"));

        String taken = "device-taken-" + UUID.randomUUID().toString().substring(0, 6);
        activate(issueInvite(workerId).path("inviteCode").asText(), taken);
        mockMvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON)
                        .content(activation(issueInvite(createWorker()).path("inviteCode").asText(), taken, key)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEVICE_ALREADY_BOUND"));

        mockMvc.perform(as("ADMIN", post(WORKERS + "/" + workerId + "/block"))).andExpect(status().isOk());
        mockMvc.perform(as("ADMIN", post(WORKERS + "/" + workerId + "/invite")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORKER_BLOCKED"));
    }

    @Test
    void activate_whenWorkerBlockedAfterInvite_denied() throws Exception {
        // given
        String workerId = createWorker();
        String code = issueInvite(workerId).path("inviteCode").asText();
        mockMvc.perform(as("ADMIN", post(WORKERS + "/" + workerId + "/block"))).andExpect(status().isOk());

        // when / then
        mockMvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON)
                        .content(activation(code, "device-blocked-1", new ECKeyGenerator(Curve.P_256).generate())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORKER_BLOCKED"));
    }

    private void activate(String code, String deviceId) throws Exception {
        mockMvc.perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON)
                        .content(activation(code, deviceId, new ECKeyGenerator(Curve.P_256).generate())))
                .andExpect(status().isOk());
    }

    private String activation(String code, String deviceId, ECKey key) throws Exception {
        return objectMapper.writeValueAsString(Map.of("inviteCode", code, "deviceId", deviceId,
                "publicKeyJwk", key.toPublicJWK().toJSONObject()));
    }

    private JsonNode issueInvite(String workerId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(as("ADMIN", post(WORKERS + "/" + workerId + "/invite")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createWorker() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("fullName", "Активация Тест", "position", "Монтажник",
                "contractor", "Дельта"));
        return objectMapper.readTree(mockMvc.perform(as("ADMIN", post(WORKERS).content(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
    }

    private JsonNode lastEvent(String key) throws Exception {
        List<String> payloads = dsl.select(Tables.OUTBOX.PAYLOAD).from(Tables.OUTBOX)
                .where(Tables.OUTBOX.MESSAGE_KEY.eq(key)).orderBy(Tables.OUTBOX.CREATED_AT.desc())
                .fetch(row -> row.value1().data());
        return objectMapper.readTree(payloads.getFirst());
    }

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON);
    }
}
