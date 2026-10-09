package ru.proxodnaya.access.features.pass.query.model;

import java.time.Instant;
import java.util.UUID;

import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Строка журнала для чтения. Снимок кадра и устройство сюда не попадают (NFR-10).
 *
 * @param id           попытка
 * @param ts           время решения
 * @param checkpointId проходная
 * @param zoneId       зона проходной, если известна
 * @param direction    направление
 * @param workerId     заявленный рабочий, если известен
 * @param decision     решение
 * @param code         причина
 * @param source       источник
 * @param faceScore    близость лица
 * @param latencyMs    от скана до решения
 * @param note         причина ручного прохода (только {@code MANUAL})
 * @param guardId      кто пропустил вручную (только {@code MANUAL})
 */
public record AttemptView(
        UUID id,
        Instant ts,
        String checkpointId,
        String zoneId,
        Direction direction,
        String workerId,
        Decision decision,
        ReasonCode code,
        AttemptSource source,
        Double faceScore,
        Integer latencyMs,
        String note,
        String guardId
) {
}
