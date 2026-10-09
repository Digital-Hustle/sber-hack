package ru.proxodnaya.access.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import ru.proxodnaya.access.config.AbstractIntegrationTest;
import ru.proxodnaya.access.features.pass.gateway.FaceGateway;
import ru.proxodnaya.access.features.pass.gateway.PeopleGateway;
import ru.proxodnaya.access.features.pass.gateway.ShiftGateway;
import ru.proxodnaya.access.features.pass.model.WorkerProfile;

/**
 * Ручной проход охранника (FR-14) и журнал проходов (FR-10/31) через HTTP и PostgreSQL.
 */
class AttemptsAndManualIntegrationTest extends AbstractIntegrationTest {

    private static final String MANUAL = "/api/v1/kiosk/manual";
    private static final String ATTEMPTS = "/api/v1/attempts";
    private static final String OWN = "/api/v1/worker/attempts";
    private static final String CHECKPOINT = "cp_main";

    @MockitoBean
    private FaceGateway face;

    @MockitoBean
    private PeopleGateway people;

    @MockitoBean
    private ShiftGateway shift;

    @Autowired
    private JdbcTemplate jdbc;

    private String workerId;

    @BeforeEach
    void setUp() {
        workerId = "w" + UUID.randomUUID().toString().substring(0, 8);
        when(people.accessProfile(workerId)).thenReturn(Optional.of(profile(workerId, "ACTIVE")));
        when(people.checkpointZone(CHECKPOINT)).thenReturn(Optional.of("zone-main"));
        when(people.checkpointZone("cp_unknown")).thenReturn(Optional.empty());
    }

    @Test
    void manual_twice_inThenOutWithPresenceAndJournal() throws Exception {
        // when / then
        mockMvc.perform(guard(post(MANUAL), "guard-1").content(manualBody(workerId, "Сломан телефон")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("MANUAL"))
                .andExpect(jsonPath("$.code").value("MANUAL_GUARD"))
                .andExpect(jsonPath("$.direction").value("IN"));
        assertThat(jdbc.queryForObject("select inside from worker_state where worker_id = ?", Boolean.class, workerId)).isTrue();
        mockMvc.perform(guard(post(MANUAL), "guard-1").content(manualBody(workerId, "Выход без телефона")))
                .andExpect(jsonPath("$.direction").value("OUT"));
        assertThat(jdbc.queryForObject("select inside from worker_state where worker_id = ?", Boolean.class, workerId)).isFalse();
        mockMvc.perform(staff(get(ATTEMPTS).param("worker", workerId).param("source", "MANUAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].direction").value("OUT"))
                .andExpect(jsonPath("$.items[1].note").value("Сломан телефон"))
                .andExpect(jsonPath("$.items[1].guardId").value("guard-1"))
                .andExpect(jsonPath("$.items[1].message").isNotEmpty())
                .andExpect(jsonPath("$.page.total").value(2));
    }

    @Test
    void manual_blockedWorker_workerBlockedNoPresence() throws Exception {
        // given
        when(people.accessProfile(workerId)).thenReturn(Optional.of(profile(workerId, "BLOCKED")));

        // when / then
        mockMvc.perform(guard(post(MANUAL), "guard-1").content(manualBody(workerId, "Очень надо")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.code").value("WORKER_BLOCKED"));
        assertThat(jdbc.queryForList("select inside from worker_state where worker_id = ? and inside", workerId)).isEmpty();
    }

    @Test
    void manual_invalidInput_400_403_404() throws Exception {
        // when / then
        mockMvc.perform(guard(post(MANUAL), "guard-1").content("{\"workerId\":\"" + workerId + "\",\"checkpointId\":\"cp_main\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(MANUAL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON).content(manualBody(workerId, "Причина")))
                .andExpect(status().isForbidden());
        mockMvc.perform(guard(post(MANUAL), "guard-1")
                        .content("{\"workerId\":\"" + workerId + "\",\"checkpointId\":\"cp_unknown\",\"note\":\"Причина\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(guard(post(MANUAL), "guard-1").content(manualBody("w-nobody", "Причина")))
                .andExpect(status().isNotFound());
    }

    @Test
    void attempts_pagedByCursor_noDuplicatesOrGaps() throws Exception {
        // given
        for (int idx = 0; idx < 5; idx++) {
            mockMvc.perform(guard(post(MANUAL), "guard-1").content(manualBody(workerId, "Проход " + idx)))
                    .andExpect(status().isOk());
        }
        List<String> ids = new ArrayList<>();
        String cursor = null;

        // when
        do {
            MockHttpServletRequestBuilder request = get(ATTEMPTS).param("worker", workerId).param("limit", "2");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = objectMapper.readTree(mockMvc.perform(staff(request))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            page.path("items").forEach(item -> ids.add(item.path("id").asText()));
            assertThat(page.path("page").path("total").asLong()).isEqualTo(5);
            cursor = page.path("page").path("next").isNull() ? null : page.path("page").path("next").asText();
        } while (cursor != null);

        // then
        assertThat(ids).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void attempts_badParamsOrRole_400_401_403() throws Exception {
        // when / then
        mockMvc.perform(staff(get(ATTEMPTS).param("decision", "MAYBE"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(staff(get(ATTEMPTS).param("cursor", "not-a-cursor"))).andExpect(status().isBadRequest());
        mockMvc.perform(staff(get(ATTEMPTS).param("limit", "0"))).andExpect(status().isBadRequest());
        mockMvc.perform(staff(get(ATTEMPTS).param("from", "2026-01-02T00:00:00Z").param("to", "2026-01-01T00:00:00Z")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ATTEMPTS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ATTEMPTS).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUARD"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void workerAttempts_onlyOwnHistory() throws Exception {
        // given
        String other = "w" + UUID.randomUUID().toString().substring(0, 8);
        when(people.accessProfile(other)).thenReturn(Optional.of(profile(other, "ACTIVE")));
        mockMvc.perform(guard(post(MANUAL), "guard-1").content(manualBody(workerId, "Своя запись")));
        mockMvc.perform(guard(post(MANUAL), "guard-1").content(manualBody(other, "Чужая запись")));

        // when / then
        mockMvc.perform(get(OWN).param("worker", other)
                        .with(jwt().jwt(token -> token.subject(workerId)).authorities(new SimpleGrantedAuthority("ROLE_WORKER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].workerId").value(workerId));
        mockMvc.perform(get(OWN).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isForbidden());
    }

    private static WorkerProfile profile(String id, String status) {
        return new WorkerProfile(id, status, "Пётр Иванов", "Монтажник", "СтройМонтаж", Set.of(),
                LocalDate.now().plusYears(1), Set.of("zone-main"));
    }

    private static MockHttpServletRequestBuilder guard(MockHttpServletRequestBuilder request, String guardId) {
        return request.with(jwt().jwt(token -> token.subject(guardId)).authorities(new SimpleGrantedAuthority("ROLE_GUARD")))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static MockHttpServletRequestBuilder staff(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SECURITY_OFFICER")));
    }

    private static String manualBody(String worker, String note) {
        return "{\"workerId\":\"" + worker + "\",\"checkpointId\":\"" + CHECKPOINT + "\",\"note\":\"" + note + "\"}";
    }
}
