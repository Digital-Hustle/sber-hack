package ru.proxodnaya.shift.features.shift.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Смена рабочего для решения о проходе.
 *
 * @param shiftId  смена
 * @param start    начало
 * @param end      конец
 * @param windowOk момент попадает в окно смены (с допуском раннего входа)
 */
public record ActiveShift(UUID shiftId, Instant start, Instant end, boolean windowOk) {
}
