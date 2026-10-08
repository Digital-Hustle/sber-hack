package ru.proxodnaya.access.features.pass.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.ChallengeAction;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Строка журнала {@code pass_attempts}.
 *
 * @param id           попытка
 * @param ts           время решения (микросекунды)
 * @param checkpointId проходная
 * @param direction    направление
 * @param workerId     владелец QR, если известен
 * @param deviceId     устройство, если известно
 * @param window       окно QR, если известно
 * @param faceScore    близость лица
 * @param livenessOk   живость
 * @param action       челлендж
 * @param decision     решение
 * @param code         причина
 * @param latencyMs    от скана до решения
 * @param source       источник
 * @param details      что посчитали и что упало (не входит в hash)
 */
public record PassRecord(
        UUID id,
        Instant ts,
        String checkpointId,
        Direction direction,
        String workerId,
        String deviceId,
        Long window,
        Double faceScore,
        Boolean livenessOk,
        ChallengeAction action,
        Decision decision,
        ReasonCode code,
        long latencyMs,
        AttemptSource source,
        Map<String, Object> details
) {
}
