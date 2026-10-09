package ru.proxodnaya.shift.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.proxodnaya.shift.config.AbstractIntegrationTest;
import ru.proxodnaya.shift.features.alert.command.service.ExitReminderService;

/**
 * B-04: смена кончилась 40 мин назад, выход не отмечен — одно напоминание; вышедшему и недавно закончившему — нет.
 */
class ExitReminderIntegrationTest extends AbstractIntegrationTest {

    private static final long HOUR = 3600;
    private static final long FORTY_MIN = 2400;
    private static final long TEN_MIN = 600;
    private static final long SHIFT_LENGTH = 8 * HOUR;
    private static final String COUNT = "select count(*) from exit_reminders where worker_id = ?";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExitReminderService reminders;

    @Autowired
    private Clock clock;

    @Test
    void shiftEndedWithoutExit_remindedOnce_exitedAndRecentNotReminded() {
        // given
        Instant now = clock.instant();
        String forgot = worker();
        String exited = worker();
        Instant forgotEnd = now.minusSeconds(FORTY_MIN);
        shift(forgot, forgotEnd);
        enter(forgot, "a", forgotEnd.minusSeconds(HOUR), null);
        shift(exited, forgotEnd);
        enter(exited, "a", forgotEnd.minusSeconds(HOUR), forgotEnd);
        String recent = worker();
        shift(recent, now.minusSeconds(TEN_MIN));
        enter(recent, "a", now.minusSeconds(HOUR), null);

        // when
        reminders.remindOverdue();
        reminders.remindOverdue();

        // then
        assertThat(count(forgot)).isEqualTo(1);
        assertThat(count(exited)).isZero();
        assertThat(count(recent)).isZero();
    }

    private void shift(String workerId, Instant end) {
        jdbc.update("insert into shifts (id, worker_id, starts_at, ends_at, created_by, created_at) values (?, ?, ?, ?, 'admin', now())",
                UUID.randomUUID(), workerId, Timestamp.from(end.minusSeconds(SHIFT_LENGTH)), Timestamp.from(end));
    }

    private void enter(String workerId, String zone, Instant entered, Instant exitedAt) {
        jdbc.update("insert into presence_intervals (id, worker_id, zone_id, entered_at, enter_attempt_id, exited_at, close_reason)"
                        + " values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), workerId, zone, Timestamp.from(entered), UUID.randomUUID(),
                exitedAt == null ? null : Timestamp.from(exitedAt), exitedAt == null ? null : "EXIT");
    }

    private long count(String workerId) {
        Long value = jdbc.queryForObject(COUNT, Long.class, workerId);
        return value == null ? 0 : value;
    }

    private static String worker() {
        return "r" + UUID.randomUUID().toString().substring(0, 8);
    }
}
