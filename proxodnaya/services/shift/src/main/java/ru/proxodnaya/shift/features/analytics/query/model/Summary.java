package ru.proxodnaya.shift.features.analytics.query.model;

import java.time.Instant;
import java.util.List;

/**
 * «Сейчас на объекте» (FR-30/32).
 *
 * @param at         момент расчёта
 * @param inside     на объекте сейчас
 * @param byZone     по зонам
 * @param incomplete открытых интервалов старше {@code PRESENCE_TTL}: вход есть, выхода нет — в {@code inside} не входят
 * @param passes     проходы за сегодня (день площадки)
 * @param method     как посчитано
 */
public record Summary(Instant at, long inside, List<ZoneCount> byZone, long incomplete, PassCounts passes, String method) {

    /**
     * Сколько человек в зоне.
     *
     * @param zoneId зона или {@code null} (зона проходной не известна)
     * @param inside человек
     */
    public record ZoneCount(String zoneId, long inside) {
    }

    /**
     * Проходы за день.
     *
     * @param allowed пропущено по QR и лицу
     * @param manual  пропущено охранником вручную
     * @param denied  отказов
     */
    public record PassCounts(long allowed, long manual, long denied) {
    }
}
