package ru.proxodnaya.shift.features.analytics.query.model;

import java.time.Instant;

/**
 * Факт присутствия: интервал с концом, до которого его считать.
 *
 * @param workerId    рабочий
 * @param enteredAt   вход
 * @param end         выход; для свежего открытого — текущий момент, для устаревшего — вход
 * @param open        ещё на объекте (открытый свежий интервал)
 * @param unconfirmed выход не отмечен: автозакрытие, повторный вход или вход старше {@code PRESENCE_TTL}
 */
public record PresenceFact(String workerId, Instant enteredAt, Instant end, boolean open, boolean unconfirmed) {
}
