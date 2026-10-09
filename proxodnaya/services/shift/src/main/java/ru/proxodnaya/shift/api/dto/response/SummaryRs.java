package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * «Сейчас на объекте».
 *
 * @param at          момент расчёта
 * @param inside      на объекте
 * @param byZone      по зонам
 * @param incomplete  открытых интервалов старше PRESENCE_TTL (в inside не входят)
 * @param passesToday проходы за сегодня
 * @param method      как посчитано
 */
public record SummaryRs(Instant at, long inside, List<ZoneInsideRs> byZone, long incomplete, PassesRs passesToday, String method) {

    /**
     * Человек в зоне.
     *
     * @param zoneId зона
     * @param inside человек
     */
    public record ZoneInsideRs(@Schema(nullable = true) String zoneId, long inside) {
    }

    /**
     * Проходы.
     *
     * @param allowed пропущено
     * @param manual  вручную охранником
     * @param denied  отказов
     */
    public record PassesRs(long allowed, long manual, long denied) {
    }
}
