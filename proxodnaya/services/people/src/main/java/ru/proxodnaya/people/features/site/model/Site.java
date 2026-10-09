package ru.proxodnaya.people.features.site.model;

import java.util.UUID;

/**
 * Объект (стройка, склад). Зоны и проходные принадлежат объекту.
 *
 * @param id      id
 * @param name    название
 * @param address адрес или {@code null}
 */
public record Site(UUID id, String name, String address) {
}
