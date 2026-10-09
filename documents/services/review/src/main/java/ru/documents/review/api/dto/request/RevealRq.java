package ru.documents.review.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Показать полный номер.
 *
 * @param documentId документ
 * @param field      поле
 */
public record RevealRq(@NotNull UUID documentId, @NotBlank String field) {
}
