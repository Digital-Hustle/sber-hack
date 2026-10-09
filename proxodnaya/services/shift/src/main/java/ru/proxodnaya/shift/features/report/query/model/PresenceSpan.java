package ru.proxodnaya.shift.features.report.query.model;

import java.time.Instant;

/**
 * Интервал присутствия для табеля: уже с концом, до которого его считать.
 *
 * @param workerId   рабочий
 * @param start      вход
 * @param end        выход, текущий момент для свежего открытого или вход для устаревшего (0 минут)
 * @param incomplete выход не отмечен: часы неполные
 */
public record PresenceSpan(String workerId, Instant start, Instant end, boolean incomplete) {
}
