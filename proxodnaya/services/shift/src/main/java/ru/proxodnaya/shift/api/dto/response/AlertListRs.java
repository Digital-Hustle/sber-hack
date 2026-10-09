package ru.proxodnaya.shift.api.dto.response;

import java.util.List;

/**
 * Лента оповещений.
 *
 * @param items новые сверху
 */
public record AlertListRs(List<AlertRs> items) {
}
