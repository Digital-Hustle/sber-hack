package ru.proxodnaya.people.features.site.model;

import java.util.UUID;

/**
 * Проходная: вход в зону {@code zoneId}, выход — из неё.
 *
 * @param id     id
 * @param zoneId зона
 * @param siteId объект зоны
 * @param name   название
 */
public record Checkpoint(UUID id, UUID zoneId, UUID siteId, String name) {
}
