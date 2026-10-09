package ru.proxodnaya.access.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Одна строка журнала проходов.
 *
 * @param id           id попытки
 * @param ts           время решения
 * @param checkpointId проходная
 * @param direction    IN | OUT
 * @param workerId     рабочий
 * @param workerName   ФИО рабочего (если известно)
 * @param decision     ALLOW | DENY | MANUAL | ERROR
 * @param code         код причины
 * @param faceScore    близость лица
 * @param source       KIOSK | MANUAL | OFFLINE | SIMULATOR
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptRs(
        UUID id,
        Instant ts,
        String checkpointId,
        String direction,
        String workerId,
        String workerName,
        String decision,
        String code,
        Double faceScore,
        String source
) {
}
