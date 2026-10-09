package ru.proxodnaya.people.api.dto.response;

/**
 * Зона.
 *
 * @param id       id
 * @param siteId   объект
 * @param name     название
 * @param capacity вместимость или {@code null}
 */
public record ZoneRs(String id, String siteId, String name, Integer capacity) {
}
