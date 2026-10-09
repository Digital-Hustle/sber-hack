package ru.proxodnaya.people.api.dto.response;

/**
 * Проходная.
 *
 * @param id     id
 * @param zoneId зона
 * @param siteId объект
 * @param name   название
 */
public record CheckpointRs(String id, String zoneId, String siteId, String name) {
}
