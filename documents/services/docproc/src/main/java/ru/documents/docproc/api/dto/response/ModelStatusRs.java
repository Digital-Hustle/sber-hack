package ru.documents.docproc.api.dto.response;

import java.time.Instant;

/**
 * Состояние модели.
 *
 * @param provider    провайдер
 * @param model       модель
 * @param visionModel vision-модель
 * @param reachable   доступна
 * @param checkedAt   когда проверено
 * @param error       ошибка
 */
public record ModelStatusRs(String provider, String model, String visionModel, boolean reachable, Instant checkedAt,
                            String error) {
}
