package ru.proxodnaya.people.features.worker.model;

import java.util.Locale;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Фильтр списка рабочих. {@code inside} считает access/shift, в people его нет.
 */
public enum WorkerFilter {
    ALL,
    ACTIVE,
    BLOCKED;

    /**
     * Разбирает параметр запроса.
     *
     * @param value {@code all|active|blocked} или {@code null}
     * @return фильтр
     * @throws DomainException VALIDATION_ERROR для неизвестного значения
     */
    public static WorkerFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Фильтр: all, active или blocked");
        }
    }
}
