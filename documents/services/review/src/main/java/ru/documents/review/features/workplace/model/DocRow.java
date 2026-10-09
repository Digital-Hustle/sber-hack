package ru.documents.review.features.workplace.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Документ в проекции; result — кэш результата docproc (карточка ≤ 500 мс).
 *
 * @param id             документ
 * @param applicationId  заявка
 * @param slotType       пункт, в который загрузил соискатель
 * @param type           вид по результату разбора
 * @param typeConfidence уверенность вида
 * @param stage          стадия
 * @param reasonCode     причина непригодности
 * @param reasons        замечания docproc
 * @param result         результат docproc
 * @param resultId       id результата
 * @param expiresOn      когда истекает (B-04)
 * @param confirmedBy    кто подтвердил
 * @param confirmedAt    когда
 * @param removed        удалён соискателем
 * @param receivedAt     получен
 * @param parsedAt       разобран
 * @param updatedAt      обновлён
 */
public record DocRow(UUID id, UUID applicationId, String slotType, String type, Double typeConfidence, String stage,
                     String reasonCode, JsonNode reasons, JsonNode result, String resultId, LocalDate expiresOn,
                     String confirmedBy, Instant confirmedAt, boolean removed, Instant receivedAt, Instant parsedAt,
                     Instant updatedAt) {

    /** Стадии, после которых разбор закончен. */
    public static final Set<String> FINAL = Set.of("PARSED", "NEEDS_REVIEW", "UNREADABLE", "FAILED");

    /** Разбор закончен. */
    public boolean finished() {
        return FINAL.contains(stage);
    }

    /** Есть поля, которые можно править и подтверждать. */
    public boolean parsed() {
        return ("PARSED".equals(stage) || "NEEDS_REVIEW".equals(stage)) && result != null;
    }
}
