package ru.proxodnaya.people.features.site.api;

import java.util.Set;
import java.util.UUID;

/**
 * Зоны для других фич (проверка допусков рабочего).
 */
public interface ZoneDirectory {

    /**
     * Каких зон из списка нет.
     *
     * @param zoneIds зоны
     * @return несуществующие
     */
    Set<UUID> missing(Set<UUID> zoneIds);
}
