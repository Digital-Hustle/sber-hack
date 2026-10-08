package ru.proxodnaya.access.features.pass.model;

import java.time.Instant;

/**
 * Присутствие рабочего на объекте.
 *
 * @param inside   отмечен вход без выхода
 * @param lastInAt время последнего входа
 */
public record Presence(boolean inside, Instant lastInAt) {

    /**
     * Нет данных — снаружи.
     *
     * @return присутствие «снаружи»
     */
    public static Presence outside() {
        return new Presence(false, null);
    }
}
