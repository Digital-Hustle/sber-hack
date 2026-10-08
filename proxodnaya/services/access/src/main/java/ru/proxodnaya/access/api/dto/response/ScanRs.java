package ru.proxodnaya.access.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Ответ на скан: {@code {kind:"decision", result}} или {@code {kind:"challenge", token, challenge, worker}}.
 *
 * @param kind      decision | challenge
 * @param result    решение (для decision)
 * @param token     токен попытки (для challenge)
 * @param challenge челлендж (для challenge)
 * @param worker    кто проходит (для challenge)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScanRs(String kind, DecisionRs result, String token, ChallengeRs challenge, WorkerRs worker) {
}
