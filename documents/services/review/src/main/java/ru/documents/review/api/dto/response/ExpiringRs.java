package ru.documents.review.api.dto.response;

import java.util.List;
import ru.documents.review.features.workplace.model.ExpiringItem;

/**
 * Отчёт «скоро истекают».
 *
 * @param days  горизонт, дней
 * @param items строки
 */
public record ExpiringRs(int days, List<ExpiringItem> items) {
}
