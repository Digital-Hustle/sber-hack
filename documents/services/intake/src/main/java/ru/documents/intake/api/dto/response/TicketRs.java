package ru.documents.intake.api.dto.response;

/**
 * Одноразовый билет SSE.
 *
 * @param ticket билет на 30 секунд
 */
public record TicketRs(String ticket) {
}
