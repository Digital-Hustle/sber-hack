package ru.proxodnaya.people.api.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Допуск со сроком.
 *
 * @param type       BRIEFING (инструктаж) | MEDICAL (медосмотр)
 * @param validUntil действует по эту дату включительно (YYYY-MM-DD)
 */
public record PermitRq(
        @NotNull(message = "Укажите вид допуска") @Pattern(regexp = "BRIEFING|MEDICAL", message = "BRIEFING или MEDICAL")
        String type,
        @NotNull(message = "Укажите срок допуска") LocalDate validUntil) {
}
