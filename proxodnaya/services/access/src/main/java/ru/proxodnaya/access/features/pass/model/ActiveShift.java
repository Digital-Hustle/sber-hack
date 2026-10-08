package ru.proxodnaya.access.features.pass.model;

import java.time.Instant;

/**
 * Активная смена из shift.
 *
 * @param shiftId  смена
 * @param start    начало
 * @param end      конец
 * @param windowOk сейчас допустимое время прохода
 */
public record ActiveShift(String shiftId, Instant start, Instant end, boolean windowOk) {
}
