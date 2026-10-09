package ru.proxodnaya.people.features.site.model;

import java.util.UUID;

/**
 * Зона объекта.
 *
 * @param id       id
 * @param siteId   объект
 * @param name     название
 * @param capacity вместимость или {@code null} — без ограничения
 */
public record Zone(UUID id, UUID siteId, String name, Integer capacity) {
}
