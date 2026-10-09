package ru.documents.docproc.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Смена вида документа оператором.
 *
 * @param type код вида
 */
public record RetypeRq(@NotBlank String type) {
}
