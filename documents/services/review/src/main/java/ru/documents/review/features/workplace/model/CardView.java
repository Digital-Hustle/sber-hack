package ru.documents.review.features.workplace.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Карточка заявки.
 *
 * @param application заявка
 * @param profile     анкета (из intake)
 * @param requests    запросы «дослать» (из intake)
 * @param contactPhone телефон
 * @param contactEmail e-mail
 * @param documents   документы: сначала требующие внимания
 * @param findings    замечания по комплекту
 * @param decision    последнее решение
 * @param history     история (последние записи)
 * @param counters    счётчики FR-54
 */
public record CardView(AppRow application, JsonNode profile, JsonNode requests, String contactPhone,
                       String contactEmail, List<DocumentCard> documents, List<Finding> findings, Decision decision,
                       List<HistoryEntry> history, Counters counters) {

    /**
     * Счётчики экономии работы (FR-54).
     *
     * @param fieldsTotal        полей всего
     * @param fieldsAttention    требуют проверки
     * @param fieldsCorrected    исправлены оператором
     * @param documentsConfirmed документов подтверждено
     * @param documentsTotal     документов всего
     */
    public record Counters(int fieldsTotal, int fieldsAttention, int fieldsCorrected, int documentsConfirmed,
                           int documentsTotal) {
    }
}
