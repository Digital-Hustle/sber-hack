package ru.proxodnaya.shift.features.analytics.query.model;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Нагрузка на проходные: день недели × час (B-08).
 *
 * @param from   начало
 * @param to     конец, не включая
 * @param zone   пояс площадки, в котором считаются часы
 * @param cells  непустые клетки
 * @param method как посчитано
 */
public record HourlyLoad(Instant from, Instant to, ZoneId zone, List<Cell> cells, String method) {

    /**
     * Клетка.
     *
     * @param weekday  0 = понедельник … 6 = воскресенье
     * @param hour     0..23
     * @param attempts попыток
     * @param allowed  пропущено (в т. ч. вручную)
     * @param denied   отказов
     */
    public record Cell(int weekday, int hour, long attempts, long allowed, long denied) {
    }
}
