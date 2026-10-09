package ru.proxodnaya.shift.integrations;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import ru.proxodnaya.shift.config.AbstractIntegrationTest;

/**
 * Смены и интервалы в БД → {@code /analytics/late-overtime}.
 */
class LateOvertimeIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/analytics/late-overtime";
    private static final long HOUR = 3600;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Test
    void lateShiftWithOvertime_andAbsentShift() throws Exception {
        // given: вчера смена 10 ч назад — 1 ч назад, вход на 30 мин позже, выход на 20 мин позже; позавчера — неявка
        String workerId = "w" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant start = now.minusSeconds(10 * HOUR);
        Instant end = now.minusSeconds(HOUR);
        UUID shiftId = shift(workerId, start, end);
        shift(workerId, start.minusSeconds(24 * HOUR), end.minusSeconds(24 * HOUR));
        jdbc.update("insert into presence_intervals (id, worker_id, zone_id, shift_id, entered_at, exited_at, close_reason, "
                        + "enter_attempt_id) values (?, ?, 'zone-main', ?, ?, ?, 'EXIT', ?)", UUID.randomUUID(), workerId, shiftId,
                Timestamp.from(start.plusSeconds(1800)), Timestamp.from(end.plusSeconds(1200)), UUID.randomUUID());

        // when / then
        mockMvc.perform(get(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .param("from", now.minusSeconds(48 * HOUR).toString()).param("to", now.toString()).param("worker", workerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].shifts").value(2))
                .andExpect(jsonPath("$.items[0].late").value(1))
                .andExpect(jsonPath("$.items[0].lateMinutes").value(30))
                .andExpect(jsonPath("$.items[0].absent").value(1))
                .andExpect(jsonPath("$.items[0].overtime").value(1))
                .andExpect(jsonPath("$.items[0].overtimeMinutes").value(20))
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0].arrival").value("ABSENT"))
                .andExpect(jsonPath("$.details[1].arrival").value("LATE"))
                .andExpect(jsonPath("$.method").isNotEmpty());
        mockMvc.perform(get(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUARD")))
                        .param("from", now.minusSeconds(HOUR).toString()).param("to", now.toString()))
                .andExpect(status().isForbidden());
    }

    private UUID shift(String workerId, Instant start, Instant end) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into shifts (id, worker_id, starts_at, ends_at, created_by, created_at) values (?, ?, ?, ?, 'admin', now())",
                id, workerId, Timestamp.from(start), Timestamp.from(end));
        return id;
    }
}
