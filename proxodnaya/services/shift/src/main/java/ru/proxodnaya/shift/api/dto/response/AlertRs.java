package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Оповещение.
 *
 * @param id         id
 * @param type       LEFT_SITE | NO_SIGNAL | ZONE_CROWDED
 * @param severity   HIGH | MEDIUM
 * @param workerId   рабочий
 * @param zoneId     зона
 * @param details    что случилось
 * @param createdAt  когда поднято
 * @param resolvedAt когда условие пропало
 * @param ackedBy    кто принял
 * @param ackedAt    когда принято
 */
public record AlertRs(UUID id, String type, String severity, String workerId, String zoneId, String details, Instant createdAt,
                      Instant resolvedAt, String ackedBy, Instant ackedAt) {
}
