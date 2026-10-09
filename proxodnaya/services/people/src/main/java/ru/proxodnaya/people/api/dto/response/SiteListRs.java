package ru.proxodnaya.people.api.dto.response;

import java.util.List;

/**
 * Список: site.
 *
 * @param items элементы по названию
 */
public record SiteListRs(List<SiteRs> items) {
}
