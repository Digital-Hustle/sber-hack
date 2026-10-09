package ru.proxodnaya.assistant.features.assistant.application.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Производные числа, которые считает сервер, а не модель.
 */
final class Numbers {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Numbers() {
    }

    /**
     * Минуты → часы с одним знаком.
     *
     * @param minutes минуты
     * @return часы
     */
    static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 1, RoundingMode.HALF_UP);
    }

    /**
     * Доля 0..1 → проценты с одним знаком.
     *
     * @param share доля
     * @return проценты
     */
    static BigDecimal percent(double share) {
        return BigDecimal.valueOf(share).multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP);
    }
}
