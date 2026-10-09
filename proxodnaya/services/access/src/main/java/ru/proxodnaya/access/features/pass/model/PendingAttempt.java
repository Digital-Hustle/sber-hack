package ru.proxodnaya.access.features.pass.model;

import java.time.Instant;
import java.util.UUID;

import ru.proxodnaya.access.shared.ChallengeAction;
import ru.proxodnaya.access.shared.Direction;

/**
 * Попытка между выдачей челленджа и приёмом кадров (таблица {@code attempt_challenges}).
 *
 * @param token          одноразовый токен попытки, он же id записи в журнале
 * @param workerId       владелец QR
 * @param deviceId       устройство
 * @param window         окно QR
 * @param direction      направление, выведенное сервером
 * @param checkpointId   проходная
 * @param zoneId         зона проходной
 * @param action         действие челленджа
 * @param framesRequired сколько кадров ждём
 * @param startedAt      когда отсканирован QR
 * @param expiresAt      до какого момента ждём кадры
 */
public record PendingAttempt(
        UUID token,
        String workerId,
        String deviceId,
        long window,
        Direction direction,
        String checkpointId,
        String zoneId,
        ChallengeAction action,
        int framesRequired,
        Instant startedAt,
        Instant expiresAt
) {
}
