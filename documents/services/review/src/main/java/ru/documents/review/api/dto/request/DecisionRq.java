package ru.documents.review.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Решение по заявке.
 *
 * @param decision ACCEPTED | RETURNED
 * @param comment  причина (для RETURNED обязательна)
 */
public record DecisionRq(@NotBlank String decision, @Size(max = 2000) String comment) {
}
