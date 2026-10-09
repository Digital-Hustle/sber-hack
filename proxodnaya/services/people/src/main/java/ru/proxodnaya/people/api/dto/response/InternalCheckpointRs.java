package ru.proxodnaya.people.api.dto.response;

/**
 * Проходная для access: куда ведёт и сколько людей вмещает зона.
 *
 * @param id           id
 * @param zoneId       зона
 * @param siteId       объект
 * @param name         название
 * @param zoneCapacity вместимость зоны или {@code null} — без ограничения
 */
public record InternalCheckpointRs(String id, String zoneId, String siteId, String name, Integer zoneCapacity) {
}
