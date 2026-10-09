package ru.proxodnaya.shift.features.interval.api;

import java.time.Instant;

/**
 * Рабочий на объекте.
 *
 * @param zoneId зона или {@code null}
 * @param since  с какого момента
 */
public record Presence(String zoneId, Instant since) {
}
