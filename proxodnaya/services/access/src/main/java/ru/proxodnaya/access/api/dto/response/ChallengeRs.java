package ru.proxodnaya.access.api.dto.response;

/**
 * Челлендж живости.
 *
 * @param kind      turn-left | turn-right | blink | nod
 * @param text      подсказка на экране
 * @param timeoutMs сколько ждём кадры
 */
public record ChallengeRs(String kind, String text, long timeoutMs) {
}
