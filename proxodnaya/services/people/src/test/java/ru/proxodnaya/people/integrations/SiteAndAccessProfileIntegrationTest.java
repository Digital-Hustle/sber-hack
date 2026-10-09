package ru.proxodnaya.people.integrations;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import ru.proxodnaya.people.config.AbstractIntegrationTest;

/**
 * Объекты, зоны, проходные, допуски рабочего и профиль доступа для access (PPL-06/08).
 */
class SiteAndAccessProfileIntegrationTest extends AbstractIntegrationTest {

    @Test
    void sitesZonesCheckpoints_createListConflictRoles() throws Exception {
        // given
        String site = create("/api/v1/sites", Map.of("name", "Объект " + suffix(), "address", "ул. Строителей, 1"));
        String zone = create("/api/v1/zones", Map.of("siteId", site, "name", "Площадка", "capacity", 50));

        // when
        String checkpoint = create("/api/v1/checkpoints", Map.of("zoneId", zone, "name", "КПП-1"));

        // then
        mockMvc.perform(as("GUARD", get("/api/v1/checkpoints").param("zoneId", zone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(checkpoint))
                .andExpect(jsonPath("$.items[0].siteId").value(site));
        mockMvc.perform(as("ADMIN", post("/api/v1/zones").content(json(Map.of("siteId", site, "name", "Площадка")))))
                .andExpect(status().isConflict());
        mockMvc.perform(as("ADMIN", post("/api/v1/zones").content(json(Map.of("siteId", UUID.randomUUID(), "name", "X")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", post("/api/v1/zones").content(json(Map.of("siteId", site, "name", "Y", "capacity", 0)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("capacity"));
        mockMvc.perform(as("MANAGER", post("/api/v1/sites").content(json(Map.of("name", "Чужой")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void worker_zonesAndPermits_validatedAndExposedInAccessProfile() throws Exception {
        // given
        String site = create("/api/v1/sites", Map.of("name", "Склад " + suffix()));
        String zoneA = create("/api/v1/zones", Map.of("siteId", site, "name", "A"));
        String zoneB = create("/api/v1/zones", Map.of("siteId", site, "name", "B"));
        String workerId = create("/api/v1/workers", Map.of("fullName", "Допуск Тест", "position", "Монтажник",
                "contractor", "Омега", "zoneIds", List.of(zoneA),
                "permits", List.of(Map.of("type", "BRIEFING", "validUntil", "2030-01-31"),
                        Map.of("type", "MEDICAL", "validUntil", "2029-06-30"))));

        // when / then — карточка
        mockMvc.perform(as("MANAGER", get("/api/v1/workers/" + workerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneIds", contains(zoneA)))
                .andExpect(jsonPath("$.permitUntil").value("2029-06-30"));
        mockMvc.perform(as("ADMIN", patch("/api/v1/workers/" + workerId).content(json(Map.of("zoneIds", List.of(zoneA, zoneB))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneIds", containsInAnyOrder(zoneA, zoneB)))
                .andExpect(jsonPath("$.permits.length()").value(2));
        mockMvc.perform(as("ADMIN", patch("/api/v1/workers/" + workerId)
                        .content(json(Map.of("zoneIds", List.of(UUID.randomUUID().toString()))))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", patch("/api/v1/workers/" + workerId).content(json(Map.of("permits", List.of(
                        Map.of("type", "MEDICAL", "validUntil", "2030-01-01"), Map.of("type", "MEDICAL", "validUntil", "2031-01-01")))))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", patch("/api/v1/workers/" + workerId)
                        .content(json(Map.of("permits", List.of(Map.of("type", "GYM", "validUntil", "2030-01-01")))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("permits[0].type"));

        // профиль для access: телефон после активации, допуски, зоны; только сервисная роль
        String code = objectMapper.readTree(mockMvc.perform(as("ADMIN", post("/api/v1/workers/" + workerId + "/invite")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("inviteCode").asText();
        String deviceId = "profile-" + suffix();
        mockMvc.perform(post("/api/v1/auth/worker/activate").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("inviteCode", code, "deviceId", deviceId,
                                "publicKeyJwk", new ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toJSONObject()))))
                .andExpect(status().isOk());
        String profile = "/internal/people/workers/" + workerId + "/access-profile";
        mockMvc.perform(as("SVC_ACCESS", get(profile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.deviceIds", contains(deviceId)))
                .andExpect(jsonPath("$.zoneIds", containsInAnyOrder(zoneA, zoneB)))
                .andExpect(jsonPath("$.permits[0].validUntil").exists());
        mockMvc.perform(as("ADMIN", get(profile))).andExpect(status().isForbidden());
        mockMvc.perform(get(profile)).andExpect(status().isUnauthorized());
        mockMvc.perform(as("SVC_ACCESS", get("/internal/people/workers/" + UUID.randomUUID() + "/access-profile")))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalCheckpoint_returnsZoneForAccess() throws Exception {
        // given
        String site = create("/api/v1/sites", Map.of("name", "КПП-объект " + suffix()));
        String zone = create("/api/v1/zones", Map.of("siteId", site, "name", "Корпус"));
        String checkpoint = create("/api/v1/checkpoints", Map.of("zoneId", zone, "name", "Вход"));

        // when / then
        mockMvc.perform(as("SVC_ACCESS", get("/internal/people/checkpoints/" + checkpoint)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zoneId").value(zone));
        mockMvc.perform(as("SVC_ACCESS", get("/internal/people/checkpoints/" + UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    private String create(String path, Map<String, Object> body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(as("ADMIN", post(path).content(json(body))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON);
    }
}
