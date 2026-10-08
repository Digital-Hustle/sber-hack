package ru.proxodnaya.access.api.dto.response;

/**
 * Финальный ответ после кадров: решение, код, сообщение, подсказка, фото рабочего.
 */
public record PassDecisionRs(
        String attemptId,
        String decision,
        String code,
        String message,
        String hint,
        Double faceScore,
        WorkerPreviewRs worker
) {}
