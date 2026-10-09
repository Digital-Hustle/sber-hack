package ru.documents.review.features.workplace.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Строка отчёта «скоро истекают» (B-04).
 *
 * @param applicationId заявка
 * @param documentId    документ
 * @param type          вид
 * @param typeName      название
 * @param applicantName ФИО
 * @param status        статус заявки
 * @param expiresOn     дата окончания
 * @param daysLeft      дней осталось (отрицательно — истёк)
 */
public record ExpiringItem(UUID applicationId, UUID documentId, String type, String typeName, String applicantName,
                           String status, LocalDate expiresOn, long daysLeft) {
}
