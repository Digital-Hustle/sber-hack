package ru.proxodnaya.shift.shared;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Размер страницы списков: 1..200, по умолчанию 50 (как в people и access).
 */
public final class PageLimit {

    public static final int DEFAULT = 50;
    public static final int MAX = 200;

    private PageLimit() {
    }

    /**
     * Проверяет размер страницы.
     *
     * @param limit из запроса или {@code null}
     * @return размер страницы
     * @throws DomainException VALIDATION_ERROR вне 1..200
     */
    public static int of(Integer limit) {
        if (limit == null) {
            return DEFAULT;
        }
        if (limit < 1 || limit > MAX) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "limit — от 1 до " + MAX);
        }
        return limit;
    }
}
