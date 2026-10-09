package ru.proxodnaya.access.features.kiosk.model;

/**
 * Проходная из people — что показать на терминале.
 *
 * @param id     проходная
 * @param zoneId зона, куда ведёт
 * @param siteId объект
 * @param name   название
 */
public record CheckpointView(String id, String zoneId, String siteId, String name) {
}
