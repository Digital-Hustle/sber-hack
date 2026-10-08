package ru.proxodnaya.access.features.pass.model;

import java.util.UUID;

import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Результат решения DecisionEngine: решение, код причины, score.
 */
public record DecisionResult(
        UUID attemptId,
        Decision decision,
        ReasonCode reasonCode,
        Double faceScore,
        String workerId
) {
    public static DecisionResult allow(UUID attemptId, String workerId, double faceScore) {
        return new DecisionResult(attemptId, Decision.ALLOW, ReasonCode.OK, faceScore, workerId);
    }

    public static DecisionResult deny(UUID attemptId, ReasonCode reason, String workerId, Double faceScore) {
        return new DecisionResult(attemptId, Decision.DENY, reason, faceScore, workerId);
    }

    public static DecisionResult error(UUID attemptId, ReasonCode reason, String workerId) {
        return new DecisionResult(attemptId, Decision.ERROR, reason, null, workerId);
    }

    public boolean isAllow() {
        return decision == Decision.ALLOW;
    }
}
