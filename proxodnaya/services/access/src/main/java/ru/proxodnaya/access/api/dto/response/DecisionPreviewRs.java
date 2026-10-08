package ru.proxodnaya.access.api.dto.response;

/**
 * Превью решения (короткий ответ, если QR невалиден и челлендж не нужен).
 */
public record DecisionPreviewRs(
        String decision,
        String code,
        String message,
        String hint
) {}
