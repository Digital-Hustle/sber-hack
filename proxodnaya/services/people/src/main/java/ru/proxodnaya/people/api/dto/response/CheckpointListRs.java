package ru.proxodnaya.people.api.dto.response;

import java.util.List;

/**
 * Список: checkpoint.
 *
 * @param items элементы по названию
 */
public record CheckpointListRs(List<CheckpointRs> items) {
}
