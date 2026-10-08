package ru.proxodnaya.access.api.dto.response;

import java.time.Instant;

/**
 * Ответ на POST /kiosk/attempts: либо челлендж (если QR валиден), либо сразу DENY.
 */
public record CreateAttemptRs(
        String attemptId,
        boolean challengeRequired,
        ChallengeRs challenge,
        DecisionPreviewRs decision
) {
    public static CreateAttemptRs challenge(String attemptId, ChallengeRs challenge) {
        return new CreateAttemptRs(attemptId, true, challenge, null);
    }

    public static CreateAttemptRs deny(String attemptId, DecisionPreviewRs decision) {
        return new CreateAttemptRs(attemptId, false, null, decision);
    }
}
