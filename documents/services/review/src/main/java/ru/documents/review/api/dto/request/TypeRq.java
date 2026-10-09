package ru.documents.review.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Смена вида документа.
 *
 * @param type код вида
 */
public record TypeRq(@NotBlank String type) {
}
