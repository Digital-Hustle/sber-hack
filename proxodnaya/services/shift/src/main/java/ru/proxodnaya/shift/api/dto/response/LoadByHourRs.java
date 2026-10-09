package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Нагрузка на проходные: день недели × час.
 *
 * @param from   начало
 * @param to     конец, не включая
 * @param zone   пояс площадки
 * @param cells  непустые клетки
 * @param peak   самая нагруженная клетка или {@code null}
 * @param method как посчитано
 */
public record LoadByHourRs(Instant from, Instant to, String zone, List<CellRs> cells, CellRs peak, String method) {

    /**
     * Клетка.
     *
     * @param weekday  0 = понедельник … 6 = воскресенье
     * @param hour     0..23
     * @param attempts попыток
     * @param allowed  пропущено
     * @param denied   отказов
     */
    public record CellRs(int weekday, int hour, long attempts, long allowed, long denied) {
    }
}
