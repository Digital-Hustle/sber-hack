package ru.proxodnaya.access.shared;

/**
 * Действие для проверки живости (active liveness challenge).
 * Сервер выдаёт случайное действие; киоск показывает подсказку и отправляет кадры.
 */
public enum ChallengeAction {
    TURN_LEFT,
    TURN_RIGHT,
    BLINK,
    NOD
}
