package ru.documents.intake.features.applications.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Заявка целиком.
 *
 * @param id                заявка
 * @param status            статус
 * @param source            WEB | DEMO
 * @param profile           ответы анкеты
 * @param requiredDocuments набор
 * @param documents         документы
 * @param missing           каких обязательных видов нет
 * @param requests          запросы оператора
 * @param decisionComment   комментарий к решению
 * @param contactPhone      телефон
 * @param contactEmail      e-mail
 * @param createdAt         создана
 * @param updatedAt         обновлена
 */
public record ApplicationView(UUID id, String status, String source, Map<String, Object> profile,
                              List<RequiredDocument> requiredDocuments, List<DocumentView> documents,
                              List<String> missing, List<OperatorRequest> requests, String decisionComment,
                              String contactPhone, String contactEmail, Instant createdAt, Instant updatedAt) {

    /**
     * Запрос оператора «дослать/переснять».
     *
     * @param id         запрос
     * @param documentId документ
     * @param type       вид
     * @param reason     причина
     * @param createdAt  когда
     * @param resolved   закрыт новой загрузкой
     */
    public record OperatorRequest(UUID id, UUID documentId, String type, String reason, Instant createdAt,
                                  boolean resolved) {
    }
}
