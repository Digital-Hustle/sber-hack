package ru.proxodnaya.access.features.pass.model;

import java.time.Duration;
import java.time.Instant;

import ru.proxodnaya.access.shared.Direction;

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

    /**
     * Направление следующего прохода (ADR-037): внутри и вход моложе {@code ttl} — выход, иначе вход.
     * Вход старше {@code ttl} считаем забытым выходом: человек не должен навсегда застрять «внутри».
     *
     * @param now момент прохода
     * @param ttl сколько считаем человека внутри после входа
     * @return IN или OUT
     */
    public Direction nextDirection(Instant now, Duration ttl) {
        boolean fresh = inside && lastInAt != null && lastInAt.isAfter(now.minus(ttl));
        return fresh ? Direction.OUT : Direction.IN;
    }
}
