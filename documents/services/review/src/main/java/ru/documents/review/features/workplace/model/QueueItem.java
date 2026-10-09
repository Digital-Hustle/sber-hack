package ru.documents.review.features.workplace.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Строка очереди.
 *
 * @param id             заявка
 * @param status         статус
 * @param source         WEB | DEMO
 * @param applicantName  ФИО (у аудитора — маска)
 * @param submittedAt    отправлена
 * @param waitingSeconds сколько ждёт решения
 * @param assignee       у кого в работе
 * @param documents      документов
 * @param processing     ещё разбираются
 * @param attention      требуют проверки (NEEDS_REVIEW, UNREADABLE, FAILED)
 * @param confirmed      подтверждены оператором
 * @param missing        чего не хватало при отправке
 * @param updatedAt      обновлена
 */
public record QueueItem(UUID id, String status, String source, String applicantName, Instant submittedAt,
                        Long waitingSeconds, String assignee, int documents, int processing, int attention,
                        int confirmed, List<String> missing, Instant updatedAt) {
}
