package ru.proxodnaya.access.shared;

import java.time.Instant;

/**
 * Челлендж живости: действие, сколько кадров нужно, срок действия.
 *
 * @param action         действие (повернуть голову, моргнуть, кивнуть)
 * @param framesRequired сколько кадров киоск должен прислать (3–5)
 * @param expiresAt      срок действия окна (10 с от выдачи)
 */
public record Challenge(
        ChallengeAction action,
        int framesRequired,
        Instant expiresAt
) {
    public String textRu() {
        return switch (action) {
            case TURN_LEFT -> "Медленно поверните голову влево";
            case TURN_RIGHT -> "Медленно поверните голову вправо";
            case BLINK -> "Моргните два раза";
            case NOD -> "Кивните";
        };
    }
}
