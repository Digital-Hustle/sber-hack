package ru.proxodnaya.access.api.dto.response;

import java.time.Instant;

/**
 * Челлендж живости: действие, сколько кадров, срок.
 */
public record ChallengeRs(
        String action,
        String text,
        int framesRequired,
        Instant expiresAt
) {}
