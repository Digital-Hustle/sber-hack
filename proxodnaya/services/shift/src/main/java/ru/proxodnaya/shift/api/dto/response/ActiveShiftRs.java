package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Смена для решения о проходе.
 *
 * @param shiftId  смена
 * @param start    начало
 * @param end      конец
 * @param windowOk момент в окне смены (с допуском раннего входа)
 */
public record ActiveShiftRs(UUID shiftId, Instant start, Instant end, boolean windowOk) {
}
