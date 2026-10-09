package ru.documents.intake.api.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * Ответы анкеты.
 *
 * @param answers ответы по id вопроса
 */
public record ProfileRq(@NotNull Map<String, Object> answers) {
}
