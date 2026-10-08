package ru.proxodnaya.access.features.pass.model;

import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Решение DecisionEngine.
 *
 * @param decision ALLOW | DENY
 * @param code     причина
 * @param score    близость лица, если считали
 */
public record DecisionResult(Decision decision, ReasonCode code, Double score) {

    /**
     * Пропуск.
     *
     * @param score близость лица
     * @return ALLOW / OK
     */
    public static DecisionResult allow(Double score) {
        return new DecisionResult(Decision.ALLOW, ReasonCode.OK, score);
    }

    /**
     * Отказ.
     *
     * @param code  причина
     * @param score близость лица, если считали
     * @return DENY с причиной
     */
    public static DecisionResult deny(ReasonCode code, Double score) {
        return new DecisionResult(Decision.DENY, code, score);
    }
}
