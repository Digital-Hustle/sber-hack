package ru.documents.review.api.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Разрешить или запретить вход.
 *
 * @param enabled разрешён ли вход
 */
public record EnabledRq(@NotNull Boolean enabled) {
}
