package ru.proxodnaya.access.shared;

import java.time.Instant;

/**
 * Челлендж живости.
 *
 * @param action         действие
 * @param framesRequired сколько кадров прислать
 * @param expiresAt      до какого момента принимаются кадры
 */
public record Challenge(ChallengeAction action, int framesRequired, Instant expiresAt) {
}
