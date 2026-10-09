package ru.documents.docproc.features.processing.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Задание разбора документа.
 *
 * @param id            задание
 * @param documentId    документ
 * @param applicationId заявка
 * @param step          текущий шаг конвейера
 * @param stage         видимая стадия
 * @param status        READY | RUNNING | DONE | CANCELLED
 * @param attempts      попыток
 * @param worker        воркер
 * @param forcedType    тип, указанный оператором
 * @param vision        включён vision-путь
 * @param createdAt     создано
 * @param startedAt     начало текущего прогона
 */
public record Job(UUID id, UUID documentId, UUID applicationId, String step, String stage, String status, int attempts,
                  String worker, String forcedType, boolean vision, Instant createdAt, Instant startedAt) {
}
