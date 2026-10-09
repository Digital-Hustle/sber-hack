package ru.documents.review.features.workplace.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * Поле в карточке.
 *
 * @param name        поле
 * @param title       подпись
 * @param value       итоговое значение (правка ?? модель); номер — маска до «показать»
 * @param modelValue  что извлекла модель
 * @param raw         как написано в документе
 * @param page        страница
 * @param bbox        рамка [x0,y0,x1,y1] в пикселях страницы
 * @param confidence  уверенность 0..1
 * @param checks      проверки [{code, ok}]
 * @param reason      почему поле пустое или сомнительное
 * @param corrected   исправлено оператором
 * @param correctedBy кем
 * @param correctedAt когда
 * @param masked      значение замаскировано
 * @param attention   перепроверить (низкая уверенность или проваленная проверка)
 */
public record FieldCard(String name, String title, String value, String modelValue, String raw, Integer page,
                        JsonNode bbox, Double confidence, JsonNode checks, String reason, boolean corrected,
                        String correctedBy, Instant correctedAt, boolean masked, boolean attention) {
}
