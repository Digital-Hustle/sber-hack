package ru.documents.review.api.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import ru.documents.review.features.workplace.model.AppRow;
import ru.documents.review.features.workplace.model.CardView;
import ru.documents.review.features.workplace.model.Decision;
import ru.documents.review.features.workplace.model.DocumentCard;
import ru.documents.review.features.workplace.model.Finding;
import ru.documents.review.features.workplace.model.HistoryEntry;

/**
 * Карточка заявки.
 *
 * @param application  заявка
 * @param profile      анкета
 * @param requests     запросы «дослать»
 * @param contactPhone телефон
 * @param contactEmail e-mail
 * @param documents    документы
 * @param findings     замечания по комплекту
 * @param decision     решение
 * @param history      история
 * @param counters     счётчики
 */
public record CardRs(AppRow application, JsonNode profile, JsonNode requests, String contactPhone,
                     String contactEmail, List<DocumentCard> documents, List<Finding> findings, Decision decision,
                     List<HistoryEntry> history, CardView.Counters counters) {
}
