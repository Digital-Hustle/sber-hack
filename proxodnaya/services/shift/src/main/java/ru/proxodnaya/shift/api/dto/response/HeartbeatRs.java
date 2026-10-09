package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;

/**
 * Принятый heartbeat.
 *
 * @param at          когда принят
 * @param insideFence в геозоне; {@code null} — геозона площадки не задана
 * @param distanceM   до центра площадки, м
 */
public record HeartbeatRs(Instant at, Boolean insideFence, Double distanceM) {
}
