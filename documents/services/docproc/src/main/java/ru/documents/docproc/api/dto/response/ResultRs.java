package ru.documents.docproc.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Результат разбора документа (docs/API.md «docproc»).
 *
 * @param documentId     документ
 * @param applicationId  заявка
 * @param stage          стадия
 * @param type           вид
 * @param typeName       название вида
 * @param typeConfidence уверенность вида
 * @param evidence       основания вида
 * @param reasons        причины и замечания [{code, params}]
 * @param fields         поля
 * @param quality        качество по страницам
 * @param mrz            MRZ
 * @param pages          страницы [{n, width, height, fileId, filePage}]
 * @param model          модель
 * @param promptVersion  версия промптов
 * @param schemaVersion  версия схемы
 * @param durations      мс по стадиям
 * @param attempts       попыток текущего шага
 * @param lastError      последняя ошибка
 * @param createdAt      когда получен
 */
public record ResultRs(UUID documentId, UUID applicationId, String stage, String type, String typeName, Double typeConfidence,
                       JsonNode evidence, JsonNode reasons, List<Field> fields, JsonNode quality, JsonNode mrz,
                       JsonNode pages, String model, String promptVersion, Integer schemaVersion, JsonNode durations,
                       Integer attempts, String lastError, Instant createdAt) {

    /**
     * Поле.
     *
     * @param name       поле
     * @param title      название
     * @param value      значение
     * @param raw        как в документе
     * @param page       страница
     * @param bbox       [x0, y0, x1, y1]
     * @param confidence уверенность
     * @param checks     проверки [{code, ok}]
     * @param reason     причина отсутствия
     */
    public record Field(String name, String title, String value, String raw, Integer page, JsonNode bbox, Double confidence,
                        JsonNode checks, String reason) {
    }
}
