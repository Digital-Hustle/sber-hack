package ru.proxodnaya.shift.features.interval.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Попытка прохода записана в проекцию (любое решение) — для живого потока.
 *
 * @param attemptId    попытка
 * @param workerId     рабочий или {@code null}, если не опознан
 * @param checkpointId пункт пропуска
 * @param zoneId       зона
 * @param direction    IN | OUT
 * @param decision     ALLOW | DENY | MANUAL
 * @param reasonCode   код причины
 * @param source       KIOSK | GUARD | SIMULATOR
 * @param ts           время решения
 * @param simulated    от симулятора
 */
public record PassRecorded(UUID attemptId, String workerId, String checkpointId, String zoneId, String direction,
                           String decision, String reasonCode, String source, Instant ts, boolean simulated) {
}
