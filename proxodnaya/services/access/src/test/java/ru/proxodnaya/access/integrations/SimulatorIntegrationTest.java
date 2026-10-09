package ru.proxodnaya.access.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.proxodnaya.access.config.AbstractIntegrationTest;
import ru.proxodnaya.access.features.pass.gateway.FaceGateway;
import ru.proxodnaya.access.features.pass.gateway.PeopleGateway;
import ru.proxodnaya.access.features.pass.gateway.ShiftGateway;

/**
 * Симулятор (FR-34): журнал и outbox с {@code source = SIMULATOR}, присутствие не меняется, только ADMIN.
 */
class SimulatorIntegrationTest extends AbstractIntegrationTest {

    private static final String RUNS = "/api/v1/simulator/runs";
    private static final String CHECKPOINT = "cp_sim";

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
        workerId = "s" + UUID.randomUUID().toString().substring(0, 8);
        when(people.checkpointZone(CHECKPOINT)).thenReturn(Optional.of("zone-main"));
        when(people.checkpointZone("cp_unknown")).thenReturn(Optional.empty());
    }

    @Test
    void run_admin_writesJournalAndOutboxWithoutPresence() throws Exception {
        // when
        mockMvc.perform(admin(post(RUNS)).content(body(CHECKPOINT, 14)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").isNumber())
                .andExpect(jsonPath("$.firstTs").isNotEmpty());

        // then
        Integer journal = jdbc.queryForObject(
                "select count(*) from pass_attempts where claimed_worker_id = ? and source = 'SIMULATOR'", Integer.class, workerId);
        assertThat(journal).isGreaterThanOrEqualTo(20);
        Integer events = jdbc.queryForObject("select count(*) from outbox where message_key = ?", Integer.class, workerId);
        assertThat(events).isEqualTo(journal);
        Integer presence = jdbc.queryForObject("select count(*) from worker_state where worker_id = ?", Integer.class, workerId);
        assertThat(presence).isZero();
    }

    @Test
    void run_notAdmin_forbidden() throws Exception {
        mockMvc.perform(post(RUNS).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON).content(body(CHECKPOINT, 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void run_tooManyDaysOrUnknownCheckpoint_rejected() throws Exception {
        mockMvc.perform(admin(post(RUNS)).content(body(CHECKPOINT, 365)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(admin(post(RUNS)).content(body("cp_unknown", 1)))
                .andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(MediaType.APPLICATION_JSON);
    }

    private String body(String checkpoint, int days) {
        return """
                {"checkpointId": "%s", "workerIds": ["%s"], "days": %d, "seed": 7}
                """.formatted(checkpoint, workerId, days);
    }
}
