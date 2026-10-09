package ru.proxodnaya.shift.features.worker.model;

import java.time.Duration;
import java.time.Instant;

import ru.proxodnaya.shift.features.shift.api.ActiveShift;

/**
 * Статус рабочего для его телефона.
 *
 * @param inside      на объекте
 * @param zoneId      зона, если на объекте
 * @param since       с какого момента на объекте
 * @param shift       смена сейчас или сегодня, или {@code null}
 * @param workedToday на объекте сегодня (день площадки)
 */
public record WorkerStatus(boolean inside, String zoneId, Instant since, ActiveShift shift, Duration workedToday) {
}
