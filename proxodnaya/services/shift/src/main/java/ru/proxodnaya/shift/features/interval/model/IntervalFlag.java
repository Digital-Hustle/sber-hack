package ru.proxodnaya.shift.features.interval.model;

/**
 * Пометки интервала: такие интервалы считаются неполными ({@code incomplete}), а не «как будто вышел».
 */
public enum IntervalFlag {
    /** Выход не отмечен, конец интервала — оценка. */
    UNCONFIRMED_EXIT
}
