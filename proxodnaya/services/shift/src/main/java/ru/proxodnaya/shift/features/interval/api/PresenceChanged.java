package ru.proxodnaya.shift.features.interval.api;

import java.time.Instant;

/**
 * Человек вошёл в зону или вышел из неё (открыт/закрыт интервал) — для живого потока.
 *
 * @param workerId    рабочий
 * @param zoneId      зона
 * @param inside      {@code true} — вошёл
 * @param at          время
 * @param closeReason причина закрытия интервала (при выходе)
 * @param simulated   от симулятора
 */
public record PresenceChanged(String workerId, String zoneId, boolean inside, Instant at, String closeReason,
                              boolean simulated) {
}
