package ru.documents.docproc.features.processing.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Результат разбора для оператора и review.
 *
 * @param documentId     документ
 * @param applicationId  заявка
 * @param stage          стадия
 * @param type           вид
 * @param typeName       название вида
 * @param typeConfidence уверенность вида
 * @param evidence       основания вида
 * @param reasons        причины и замечания
 * @param fields         поля
 * @param quality        качество по страницам
 * @param mrz            MRZ
 * @param pages          страницы
 * @param model          модель
 * @param promptVersion  версия промптов
 * @param schemaVersion  версия схемы
 * @param durations      мс по стадиям
 * @param attempts       попыток текущего шага
 * @param lastError      последняя ошибка
 * @param createdAt      когда получен результат
 */
public record ResultView(UUID documentId, UUID applicationId, String stage, String type, String typeName,
                         Double typeConfidence, JsonNode evidence, JsonNode reasons, List<FieldView> fields,
                         JsonNode quality, JsonNode mrz, JsonNode pages, String model, String promptVersion,
                         Integer schemaVersion, JsonNode durations, Integer attempts, String lastError,
                         Instant createdAt) {

    /**
     * Поле.
     *
     * @param name       поле
     * @param title      название
     * @param value      значение
     * @param raw        как в документе
     * @param page       страница
     * @param bbox       рамка
     * @param confidence уверенность
     * @param checks     проверки
     * @param reason     причина отсутствия
     */
    public record FieldView(String name, String title, String value, String raw, Integer page, JsonNode bbox,
                            Double confidence, JsonNode checks, String reason) {
    }
}
