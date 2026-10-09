package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Отработанные часы за период.
 *
 * @param from   начало
 * @param to     конец, не включая
 * @param items  по рабочим
 * @param method как посчитано
 */
public record HoursRs(Instant from, Instant to, List<WorkerHoursRs> items, String method) {

    /**
     * Рабочий.
     *
     * @param workerId      рабочий
     * @param workedMinutes минут
     * @param intervals     интервалов
     * @param incomplete    неполных интервалов
     */
    public record WorkerHoursRs(String workerId, long workedMinutes, long intervals, long incomplete) {
    }
}
