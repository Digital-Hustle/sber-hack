package ru.proxodnaya.shift.shared;

import java.time.Instant;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Полуоткрытый период {@code [from, until)}; любая граница может отсутствовать.
 *
 * @param from  начало, включительно
 * @param until конец, не включая
 */
public record Period(Instant from, Instant until) {

    /**
     * Проверяет порядок границ.
     *
     * @param from  начало
     * @param until конец
     * @return период
     * @throws DomainException VALIDATION_ERROR, если начало не раньше конца
     */
    public static Period of(Instant from, Instant until) {
        if (from != null && until != null && !from.isBefore(until)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Начало периода должно быть раньше конца");
        }
        return new Period(from, until);
    }
}
