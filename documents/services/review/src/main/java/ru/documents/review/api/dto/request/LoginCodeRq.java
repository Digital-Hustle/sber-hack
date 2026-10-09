package ru.documents.review.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Запрос кода входа.
 *
 * @param email рабочая почта
 */
public record LoginCodeRq(@NotBlank @Size(max = 254) String email) {
}
