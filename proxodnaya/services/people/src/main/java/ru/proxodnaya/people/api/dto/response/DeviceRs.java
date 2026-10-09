package ru.proxodnaya.people.api.dto.response;

import java.time.Instant;

/**
 * Устройство рабочего.
 *
 * @param deviceId  id
 * @param status    ACTIVE | REVOKED
 * @param boundAt   привязано
 * @param revokedAt отвязано
 */
public record DeviceRs(String deviceId, String status, Instant boundAt, Instant revokedAt) {
}
