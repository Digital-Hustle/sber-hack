package ru.proxodnaya.shift.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.proxodnaya.common.event.EventEnvelope;
import ru.proxodnaya.shift.config.AbstractIntegrationTest;
import ru.proxodnaya.shift.features.interval.command.service.IntervalService;

/**
 * {@code access.pass.v1} через Kafka → интервалы → аналитика и статус рабочего.
 */
class PassFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String TOPIC = "access.pass.v1";
    private static final long WAIT_MS = 20_000;
    private static final long POLL_MS = 100;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IntervalService intervals;

    @Autowired
    private Clock clock;

    private String workerId;
    private Instant now;

    @BeforeEach
    void setUp() {
        workerId = "w" + UUID.randomUUID().toString().substring(0, 8);
        now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void entryThenExit_closedIntervalAndHours() throws Exception {
        // given
        Instant entered = now.minusSeconds(3 * 3600);

        // when
        send(UUID.randomUUID(), pass("IN", "ALLOW", "zone-main", entered, "KIOSK"));
        send(UUID.randomUUID(), pass("OUT", "ALLOW", "zone-main", entered.plusSeconds(2 * 3600), "KIOSK"));

        // then
        await(() -> count("select count(*) from presence_intervals where worker_id = ? and close_reason = 'EXIT'") == 1);
        mockMvc.perform(staff(get("/api/v1/intervals")).param("worker", workerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].closeReason").value("EXIT"))
                .andExpect(jsonPath("$.items[0].incomplete").value(false));
        mockMvc.perform(staff(get("/api/v1/analytics/hours")).param("worker", workerId)
                        .param("from", now.minusSeconds(24 * 3600).toString()).param("to", now.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].workedMinutes").value(120))
                .andExpect(jsonPath("$.items[0].incomplete").value(0))
                .andExpect(jsonPath("$.method").isNotEmpty());
        assertThat(count("select count(*) from outbox where message_key = ?")).isEqualTo(2);
    }

    @Test
    void duplicateEvent_appliedOnce_denyOnlyProjected() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        Map<String, Object> entry = pass("IN", "ALLOW", "zone-main", now.minusSeconds(60), "KIOSK");

        // when
        send(eventId, entry);
        send(eventId, entry);
        send(UUID.randomUUID(), pass("IN", "DENY", "zone-main", now.minusSeconds(30), "KIOSK"));

        // then
        await(() -> count("select count(*) from pass_events where worker_id = ?") == 2);
        assertThat(count("select count(*) from presence_intervals where worker_id = ?")).isEqualTo(1);
    }

    @Test
    void entryNow_summaryAndWorkerMe() throws Exception {
        // given
        String zone = "zone-" + workerId;
        send(UUID.randomUUID(), pass("IN", "MANUAL", zone, now.minusSeconds(600), "MANUAL"));
        await(() -> count("select count(*) from presence_intervals where worker_id = ?") == 1);

        // when / then
        mockMvc.perform(staff(get("/api/v1/analytics/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byZone[?(@.zoneId == '" + zone + "')].inside").value(1))
                .andExpect(jsonPath("$.passesToday.manual").isNumber());
        mockMvc.perform(get("/api/v1/worker/me")
                        .with(jwt().jwt(token -> token.subject(workerId)).authorities(new SimpleGrantedAuthority("ROLE_WORKER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inside").value(true))
                .andExpect(jsonPath("$.zoneId").value(zone))
                .andExpect(jsonPath("$.workedMsToday").isNumber());
        mockMvc.perform(get("/api/v1/worker/me").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void simulatorEntry_excludedByDefault() throws Exception {
        // given
        send(UUID.randomUUID(), pass("IN", "ALLOW", "zone-main", now.minusSeconds(60), "SIMULATOR"));
        await(() -> count("select count(*) from presence_intervals where worker_id = ?") == 1);

        // when / then
        mockMvc.perform(staff(get("/api/v1/intervals")).param("worker", workerId))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(staff(get("/api/v1/intervals")).param("worker", workerId).param("simulated", "true"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void simulatorHistory_separateChain_realIntervalKept_hoursByFlag() throws Exception {
        // given: человек сейчас на объекте 2 ч, симулятор досылает вчерашний вход и выход (1 ч)
        Instant yesterday = now.minusSeconds(26 * 3600);
        send(UUID.randomUUID(), pass("IN", "ALLOW", "zone-main", now.minusSeconds(2 * 3600), "KIOSK"));

        // when
        send(UUID.randomUUID(), pass("IN", "ALLOW", "zone-main", yesterday, "SIMULATOR"));
        send(UUID.randomUUID(), pass("OUT", "ALLOW", "zone-main", yesterday.plusSeconds(3600), "SIMULATOR"));

        // then
        await(() -> count("select count(*) from presence_intervals where worker_id = ? and simulated and close_reason = 'EXIT'") == 1);
        assertThat(count("select count(*) from presence_intervals where worker_id = ? and not simulated and exited_at is null"))
                .isEqualTo(1);
        String from = now.minusSeconds(48 * 3600).toString();
        mockMvc.perform(staff(get("/api/v1/analytics/hours")).param("worker", workerId).param("from", from).param("to", now.toString()))
                .andExpect(jsonPath("$.items[0].workedMinutes").value(120))
                .andExpect(jsonPath("$.method").value(Matchers.containsString("Симулятор исключён")));
        mockMvc.perform(staff(get("/api/v1/analytics/hours")).param("worker", workerId).param("from", from).param("to", now.toString())
                        .param("simulated", "true"))
                .andExpect(jsonPath("$.items[0].workedMinutes").value(180))
                .andExpect(jsonPath("$.items[0].intervals").value(2))
                .andExpect(jsonPath("$.method").value(Matchers.containsString("демо-историю симулятора")));
    }

    @Test
    void entryOnShiftWithoutExit_autoClosedAtShiftEndUnconfirmed() throws Exception {
        // given: смена закончилась 3 ч назад, выход не отмечен
        Instant start = now.minusSeconds(12 * 3600);
        Instant end = now.minusSeconds(3 * 3600);
        jdbc.update("insert into shifts (id, worker_id, starts_at, ends_at, created_by, created_at) values (?, ?, ?, ?, 'admin', now())",
                UUID.randomUUID(), workerId, java.sql.Timestamp.from(start), java.sql.Timestamp.from(end));
        send(UUID.randomUUID(), pass("IN", "ALLOW", "zone-main", start.plusSeconds(600), "KIOSK"));
        await(() -> count("select count(*) from presence_intervals where worker_id = ? and shift_id is not null") == 1);

        // when
        intervals.autoClose();

        // then
        mockMvc.perform(staff(get("/api/v1/intervals")).param("worker", workerId))
                .andExpect(jsonPath("$.items[0].closeReason").value("AUTO_SHIFT_END"))
                .andExpect(jsonPath("$.items[0].exitedAt").value(end.toString()))
                .andExpect(jsonPath("$.items[0].flags[0]").value("UNCONFIRMED_EXIT"))
                .andExpect(jsonPath("$.items[0].incomplete").value(true));
    }

    @Test
    void analytics_badPeriodOrRole_400_403() throws Exception {
        // when / then
        mockMvc.perform(staff(get("/api/v1/analytics/hours")).param("from", now.toString()).param("to", now.minusSeconds(1).toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(staff(get("/api/v1/analytics/hours")).param("from", now.toString())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/analytics/summary").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_WORKER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/summary")).andExpect(status().isUnauthorized());
    }

    private Map<String, Object> pass(String direction, String decision, String zone, Instant moment, String source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attemptId", UUID.randomUUID().toString());
        payload.put("workerId", workerId);
        payload.put("checkpointId", "cp_main");
        payload.put("zoneId", zone);
        payload.put("direction", direction);
        payload.put("decision", decision);
        payload.put("reasonCode", "OK");
        payload.put("ts", moment.toString());
        payload.put("source", source);
        return payload;
    }

    private void send(UUID eventId, Map<String, Object> payload) throws Exception {
        EventEnvelope envelope = new EventEnvelope(eventId.toString(), "PassDecided", now, workerId, null, payload);
        kafka.send(TOPIC, workerId, objectMapper.writeValueAsString(envelope)).get();
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class, workerId);
        return value == null ? 0 : value;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("не дождались обработки события").isLessThan(deadline);
            Thread.sleep(POLL_MS);
        }
    }

    private static MockHttpServletRequestBuilder staff(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")));
    }
}
