package ru.proxodnaya.access.features.pass.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import ru.proxodnaya.access.shared.Direction;

/**
 * Контекст решения о проходе: все данные, собранные на этапе проверки.
 * DecisionEngine использует это для пошаговой проверки.
 *
 * @param attemptId      id попытки (генерируется при приёме QR)
 * @param qr             распарсенный QR (deviceId, window, signature)
 * @param direction      направление (IN/OUT)
 * @param checkpointId   id проходной
 * @param zoneId         id зоны, к которой относится проходная
 * @param devicePublicKey публичный ключ устройства (X.509 DER)
 * @param workerId       id рабочего (из device_keys)
 * @param workerStatus   статус рабочего (ACTIVE/BLOCKED)
 * @param permitUntil    срок допуска (инструктаж/медосмотр)
 * @param workerZoneIds  зоны, в которые у рабочего есть допуск
 * @param faceResult     результат сверки лица (null = ещё не проверено)
 * @param shiftActive     есть активная смена
 * @param shiftWindowOk   время в окне смены
 * @param isInside        рабочий на объекте (anti-passback)
 * @param zoneOccupancy   текущая заполненность зоны
 * @param zoneCapacity    вместимость зоны
 * @param recentFailures  число недавних неудач (для TEMP_LOCKED)
 * @param now             текущее время
 */
public record PassContext(
        UUID attemptId,
        ru.proxodnaya.access.features.qr.model.QrPayload qr,
        Direction direction,
        String checkpointId,
        String zoneId,
        byte[] devicePublicKey,
        String workerId,
        String workerStatus,
        java.time.LocalDate permitUntil,
        Set<String> workerZoneIds,
        FaceResult faceResult,
        boolean shiftActive,
        boolean shiftWindowOk,
        boolean isInside,
        int zoneOccupancy,
        int zoneCapacity,
        int recentFailures,
        Instant now
) {
}
