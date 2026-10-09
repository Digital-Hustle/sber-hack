package ru.proxodnaya.people.api.dto.response;

import java.time.LocalDate;

/**
 * Допуск со сроком.
 *
 * @param type       BRIEFING | MEDICAL
 * @param validUntil действует по дату включительно
 */
public record PermitRs(String type, LocalDate validUntil) {
}
