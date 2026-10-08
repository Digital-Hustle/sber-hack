package ru.proxodnaya.access.api.dto.response;

/**
 * Короткая информация о рабочем для ответа киоску.
 */
public record WorkerPreviewRs(
        String name,
        String photoThumb
) {}
