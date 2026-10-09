package ru.proxodnaya.shift.features.alert.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Пора напомнить рабочему отметить выход (для SSE {@code reminder.exit}); публикуется в транзакции записи.
 *
 * @param shiftId  смена
 * @param workerId рабочий
 * @param endsAt   конец смены
 * @param sentAt   когда напомнили
 */
public record ExitReminderDue(UUID shiftId, String workerId, Instant endsAt, Instant sentAt) {
}
