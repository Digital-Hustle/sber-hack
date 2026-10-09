package ru.documents.intake.api.dto.response;

import java.util.List;

/**
 * Комплект отправлен.
 *
 * @param status  статус
 * @param missing чего не хватает
 */
public record SubmitRs(String status, List<String> missing) {
}
