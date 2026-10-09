package ru.proxodnaya.access.api.dto.response;

/**
 * Проходная терминала.
 *
 * @param id     проходная
 * @param zoneId зона
 * @param siteId объект
 * @param name   название
 */
public record KioskCheckpointRs(String id, String zoneId, String siteId, String name) {
}
