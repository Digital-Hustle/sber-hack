package ru.proxodnaya.people.api.dto.response;

import java.util.List;

/**
 * Устройства рабочего.
 *
 * @param items устройства, новые сверху
 */
public record DeviceListRs(List<DeviceRs> items) {
}
