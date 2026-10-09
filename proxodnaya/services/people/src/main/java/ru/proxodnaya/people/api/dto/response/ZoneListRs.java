package ru.proxodnaya.people.api.dto.response;

import java.util.List;

/**
 * Список: zone.
 *
 * @param items элементы по названию
 */
public record ZoneListRs(List<ZoneRs> items) {
}
