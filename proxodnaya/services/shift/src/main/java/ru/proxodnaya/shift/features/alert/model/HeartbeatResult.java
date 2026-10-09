package ru.proxodnaya.shift.features.alert.model;

import java.time.Instant;

/**
 * Принятый heartbeat.
 *
 * @param at          когда принят
 * @param insideFence в геозоне или {@code null} — геозона не задана
 * @param distanceM   до центра площадки, м, или {@code null}
 */
public record HeartbeatResult(Instant at, Boolean insideFence, Double distanceM) {
}
