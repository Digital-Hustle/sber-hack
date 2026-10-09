package ru.documents.review.features.workplace.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Документ в карточке.
 *
 * @param id             документ
 * @param slotType       пункт соискателя
 * @param type           вид по разбору
 * @param typeName       название вида
 * @param typeConfidence уверенность вида
 * @param stage          стадия
 * @param reasonCode     причина непригодности
 * @param reasons        замечания docproc [{code, params}]
 * @param files          файлы из intake [{id, version, active, mime, pages}]
 * @param pages          размеры страниц результата [{n, width, height}] — система координат bbox
 * @param quality        метрики качества
 * @param model          модель
 * @param promptVersion  версия промпта
 * @param expiresOn      истекает
 * @param confirmed      подтверждён
 * @param confirmedBy    кем
 * @param confirmedAt    когда
 * @param attention      требует внимания
 * @param fields         поля: сначала «перепроверить»
 */
public record DocumentCard(UUID id, String slotType, String type, String typeName, Double typeConfidence,
                           String stage, String reasonCode, JsonNode reasons, JsonNode files, JsonNode pages,
                           JsonNode quality, String model, String promptVersion, LocalDate expiresOn,
                           boolean confirmed, String confirmedBy, Instant confirmedAt, boolean attention,
                           List<FieldCard> fields) {
}
