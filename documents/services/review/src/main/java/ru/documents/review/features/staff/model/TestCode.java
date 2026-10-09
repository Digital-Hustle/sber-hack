package ru.documents.review.features.staff.model;

/**
 * Код для тестового входа (только APP_MODE=dev).
 *
 * @param code 6 цифр — форма сразу отправляет их в Keycloak
 */
public record TestCode(String code) {
}
