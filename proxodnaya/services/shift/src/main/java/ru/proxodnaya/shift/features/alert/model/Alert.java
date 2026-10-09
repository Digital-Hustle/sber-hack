package ru.proxodnaya.shift.features.alert.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Оповещение.
 *
 * @param id         id
 * @param type       тип
 * @param severity   важность
 * @param workerId   рабочий или {@code null}
 * @param zoneId     зона или {@code null}
 * @param details    что случилось, по-русски
 * @param createdAt  когда поднято
 * @param resolvedAt когда условие пропало или {@code null}
 * @param ackedBy    кто принял или {@code null}
 * @param ackedAt    когда принято или {@code null}
 */
public record Alert(UUID id, AlertType type, String severity, String workerId, String zoneId, String details, Instant createdAt,
                    Instant resolvedAt, String ackedBy, Instant ackedAt) {
}
