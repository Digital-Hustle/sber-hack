package ru.proxodnaya.shift.features.analytics.query.model;

/**
 * Отработанное время рабочего за период.
 *
 * @param workerId      рабочий
 * @param workedMinutes минут на объекте (интервалы обрезаны границами периода)
 * @param intervals     интервалов в периоде
 * @param incomplete    из них неполных: выход не подтверждён или интервал висит дольше {@code PRESENCE_TTL}
 */
public record WorkerHours(String workerId, long workedMinutes, long intervals, long incomplete) {
}
