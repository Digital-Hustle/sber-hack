package ru.proxodnaya.access.features.pass.gateway;

import java.time.LocalDate;
import java.util.Set;

/**
 * Профиль доступа рабочего: статус, устройства, допуски, зоны.
 * Берётся из people через /internal/people/workers/{id}/access-profile.
 */
public record AccessProfile(
        String workerId,
        String status,        // ACTIVE | BLOCKED
        LocalDate permitUntil,
        Set<String> zoneIds,
        Set<String> deviceIds
) {}
