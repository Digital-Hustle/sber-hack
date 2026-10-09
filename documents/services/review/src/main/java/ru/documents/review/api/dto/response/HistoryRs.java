package ru.documents.review.api.dto.response;

import java.util.List;
import ru.documents.review.features.workplace.model.HistoryEntry;

/**
 * История заявки.
 *
 * @param items записи, новые сверху
 */
public record HistoryRs(List<HistoryEntry> items) {
}
