package ru.proxodnaya.shift.features.alert.model;

import java.time.Instant;

/**
 * Человек на объекте в открытой смене с последним heartbeat.
 *
 * @param workerId    рабочий
 * @param zoneId      зона
 * @param enteredAt   вход
 * @param heartbeatAt последний heartbeat или {@code null} — телефон не присылал
 * @param insideFence в геозоне по последнему heartbeat или {@code null}
 * @param distanceM   до центра площадки, м, или {@code null}
 */
public record OnSiteWorker(String workerId, String zoneId, Instant enteredAt, Instant heartbeatAt, Boolean insideFence,
                           Double distanceM) {
}
