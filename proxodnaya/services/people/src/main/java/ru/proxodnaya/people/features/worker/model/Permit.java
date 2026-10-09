package ru.proxodnaya.people.features.worker.model;

import java.time.LocalDate;

/**
 * Допуск рабочего.
 *
 * @param type       вид
 * @param validUntil действует по эту дату включительно
 */
public record Permit(PermitType type, LocalDate validUntil) {
}
