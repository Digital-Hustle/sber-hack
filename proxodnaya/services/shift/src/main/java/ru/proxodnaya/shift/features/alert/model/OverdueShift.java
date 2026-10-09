package ru.proxodnaya.shift.features.alert.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Смена кончилась, а у рабочего открыт интервал.
 *
 * @param shiftId   смена
 * @param workerId  рабочий
 * @param startsAt  начало смены
 * @param endsAt    конец смены
 * @param enteredAt вход открытого интервала
 */
public record OverdueShift(UUID shiftId, String workerId, Instant startsAt, Instant endsAt, Instant enteredAt) {
}
