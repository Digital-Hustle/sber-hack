package ru.proxodnaya.shift.features.alert.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Поднято оповещение (для SSE {@code alert.created}); публикуется в транзакции записи.
 *
 * @param alertId   id
 * @param type      тип
 * @param severity  важность
 * @param workerId  рабочий или {@code null}
 * @param zoneId    зона или {@code null}
 * @param details   что случилось
 * @param createdAt когда
 */
public record AlertRaised(UUID alertId, String type, String severity, String workerId, String zoneId, String details,
                          Instant createdAt) {
}
