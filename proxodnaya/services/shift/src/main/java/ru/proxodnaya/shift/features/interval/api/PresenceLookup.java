package ru.proxodnaya.shift.features.interval.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Присутствие рабочего для других фич (кабинет рабочего).
 */
public interface PresenceLookup {

    /**
     * Открытый интервал моложе {@code PRESENCE_TTL} — человек на объекте.
     *
     * @param workerId рабочий
     * @param now      текущий момент
     * @return зона и момент входа или пусто
     */
    Optional<Presence> current(String workerId, Instant now);

    /**
     * Сколько рабочий был на объекте в {@code [from, until)}. Открытый свежий интервал считается до {@code now}, открытый
     * старше {@code PRESENCE_TTL} — не считается (выход не отмечен, время неизвестно).
     *
     * @param workerId рабочий
     * @param from     начало
     * @param until    конец
     * @param now      текущий момент
     * @return длительность
     */
    Duration worked(String workerId, Instant from, Instant until, Instant now);
}
