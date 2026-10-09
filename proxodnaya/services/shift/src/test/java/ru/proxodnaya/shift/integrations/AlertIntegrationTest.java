package ru.proxodnaya.shift.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ru.proxodnaya.shift.config.AbstractIntegrationTest;
import ru.proxodnaya.shift.features.alert.command.service.AlertService;

/**
 * Heartbeat вне геозоны → LEFT_SITE (один раз), скопление в зоне, приём оповещения, снятие при возврате.
 */
class AlertIntegrationTest extends AbstractIntegrationTest {

    private static final String HEARTBEAT = "/api/v1/worker/heartbeat";
    private static final String ALERTS = "/api/v1/alerts";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AlertService alerts;

    @Autowired
    private Clock clock;

    @Test
    void heartbeatOutsideFence_leftSiteRaisedOnce_ackThenResolvedOnReturn() throws Exception {
        // given: смена идёт, человек вошёл 10 мин назад в свою зону
        String workerId = "w" + UUID.randomUUID().toString().substring(0, 8);
        String zone = "zone-" + workerId;
        Instant now = clock.instant();
        jdbc.update("insert into shifts (id, worker_id, starts_at, ends_at, created_by, created_at) values (?, ?, ?, ?, 'admin', now())",
                UUID.randomUUID(), workerId, Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now.plusSeconds(3600)));
        enter(workerId, zone, now.minusSeconds(600));

        // when: телефон в ~1,1 км от центра
        mockMvc.perform(post(HEARTBEAT).with(worker(workerId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":55.76,\"lon\":37.62,\"accuracy\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insideFence").value(false));
        alerts.runRules();
        alerts.runRules();

        // then
        assertThat(count("select count(*) from alerts where worker_id = ? and type = 'LEFT_SITE'", workerId)).isEqualTo(1);
        assertThat(count("select count(*) from outbox where topic = 'shift.alert.v1' and message_key = ?", workerId)).isEqualTo(1);
        String id = jdbc.queryForObject("select id::text from alerts where worker_id = ?", String.class, workerId);
        mockMvc.perform(get(ALERTS).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].type").value("LEFT_SITE"));
        mockMvc.perform(post(ALERTS + "/" + id + "/ack").with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ackedBy").value("officer"));

        // when: вернулся в геозону
        mockMvc.perform(post(HEARTBEAT).with(worker(workerId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":55.7501,\"lon\":37.6201}"))
                .andExpect(jsonPath("$.insideFence").value(true));
        alerts.runRules();

        // then
        assertThat(count("select count(*) from alerts where worker_id = ? and resolved_at is not null", workerId)).isEqualTo(1);
    }

    @Test
    void twoInZoneOverLimit_zoneCrowded_badInput_rejected() throws Exception {
        // given: лимит 1 человек (application-test.yml)
        String zone = "crowd-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = clock.instant();
        enter("a-" + zone, zone, now.minusSeconds(60));
        enter("b-" + zone, zone, now.minusSeconds(60));

        // when
        alerts.runRules();

        // then
        assertThat(count("select count(*) from alerts where zone_id = ? and type = 'ZONE_CROWDED' and resolved_at is null", zone))
                .isEqualTo(1);
        mockMvc.perform(post(HEARTBEAT).with(worker("x")).contentType(MediaType.APPLICATION_JSON).content("{\"lat\":120,\"lon\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ALERTS).with(staff()).param("status", "bogus"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ALERTS + "/" + UUID.randomUUID() + "/ack").with(staff()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ALERTS).with(worker("x")))
                .andExpect(status().isForbidden());
    }

    private void enter(String workerId, String zone, Instant moment) {
        jdbc.update("insert into presence_intervals (id, worker_id, zone_id, entered_at, enter_attempt_id) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), workerId, zone, Timestamp.from(moment), UUID.randomUUID());
    }

    private long count(String sql, String arg) {
        Long value = jdbc.queryForObject(sql, Long.class, arg);
        return value == null ? 0 : value;
    }

    private static RequestPostProcessor worker(String workerId) {
        return jwt().jwt(token -> token.subject(workerId)).authorities(new SimpleGrantedAuthority("ROLE_WORKER"));
    }

    private static RequestPostProcessor staff() {
        return jwt().jwt(token -> token.subject("officer")).authorities(new SimpleGrantedAuthority("ROLE_SECURITY_OFFICER"));
    }
}
