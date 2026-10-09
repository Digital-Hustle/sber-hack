package ru.proxodnaya.shift.integrations;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ru.proxodnaya.shift.config.AbstractIntegrationTest;

/**
 * Проекция попыток → отказы по причинам и нагрузка по часам.
 */
class PassStatsIntegrationTest extends AbstractIntegrationTest {

    private static final String FROM = "2026-03-02T00:00:00Z";
    private static final String TO = "2026-03-03T00:00:00Z";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void refusalsAndLoad_byCheckpoint_simulatorExcluded() throws Exception {
        // given: понедельник 2 марта 2026, 09:10 МСК = 06:10Z
        String checkpoint = "cp-" + UUID.randomUUID().toString().substring(0, 8);
        pass(checkpoint, "ALLOW", "OK", "KIOSK", "2026-03-02T06:10:00Z");
        pass(checkpoint, "DENY", "FACE_LOW_QUALITY", "KIOSK", "2026-03-02T06:11:00Z");
        pass(checkpoint, "DENY", "NO_SHIFT", "KIOSK", "2026-03-02T06:12:00Z");
        pass(checkpoint, "DENY", "NO_SHIFT", "KIOSK", "2026-03-02T06:13:00Z");
        pass(checkpoint, "DENY", "NO_SHIFT", "SIMULATOR", "2026-03-02T06:14:00Z");

        // when / then
        mockMvc.perform(get("/api/v1/analytics/refusals").with(staff()).param("from", FROM).param("to", TO).param("checkpoint", checkpoint))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(4))
                .andExpect(jsonPath("$.denied").value(3))
                .andExpect(jsonPath("$.deniedShare").value(0.75))
                .andExpect(jsonPath("$.system").value(1))
                .andExpect(jsonPath("$.legitimate").value(2))
                .andExpect(jsonPath("$.byReason[0].reasonCode").value("NO_SHIFT"))
                .andExpect(jsonPath("$.byReason[0].category").value("LEGITIMATE"))
                .andExpect(jsonPath("$.byReason[1].category").value("SYSTEM"));
        mockMvc.perform(get("/api/v1/analytics/load-by-hour").with(staff()).param("from", FROM).param("to", TO)
                        .param("checkpoint", checkpoint))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cells.length()").value(1))
                .andExpect(jsonPath("$.peak.weekday").value(0))
                .andExpect(jsonPath("$.peak.hour").value(9))
                .andExpect(jsonPath("$.peak.attempts").value(4))
                .andExpect(jsonPath("$.peak.denied").value(3));
        mockMvc.perform(get("/api/v1/analytics/refusals").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUARD"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/load-by-hour").with(staff()).param("from", TO).param("to", FROM))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor staff() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_SECURITY_OFFICER"));
    }

    private void pass(String checkpoint, String decision, String reason, String source, String moment) {
        jdbc.update("insert into pass_events (attempt_id, worker_id, checkpoint_id, zone_id, direction, decision, reason_code, source, ts) "
                        + "values (?, 'w1', ?, 'zone-main', 'IN', ?, ?, ?, ?)", UUID.randomUUID(), checkpoint, decision, reason, source,
                Timestamp.from(Instant.parse(moment)));
    }
}
