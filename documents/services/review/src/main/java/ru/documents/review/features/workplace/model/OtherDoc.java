package ru.documents.review.features.workplace.model;

import java.util.UUID;
import ru.documents.review.features.workplace.rules.DocFacts;

/**
 * Документ другой заявки — кандидат на совпадение номера (FR-03).
 *
 * @param applicationId заявка
 * @param facts         итоговые значения полей
 */
public record OtherDoc(UUID applicationId, DocFacts facts) {
}
