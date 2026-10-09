package ru.proxodnaya.access.api.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Строка журнала проходов.
 *
 * @param id           попытка
 * @param ts           время решения
 * @param checkpointId проходная
 * @param zoneId       зона проходной, если известна
 * @param direction    IN | OUT
 * @param workerId     заявленный рабочий, если известен
 * @param decision     ALLOW | DENY | MANUAL | ERROR
 * @param code         код причины (таблица в docs/API.md)
 * @param message      текст причины по-русски
 * @param source       KIOSK | MANUAL | OFFLINE | SIMULATOR
 * @param score        близость лица
 * @param latencyMs    от скана до решения
 * @param note         причина ручного прохода
 * @param guardId      кто пропустил вручную
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptRs(
        String id,
        Instant ts,
        String checkpointId,
        String zoneId,
        String direction,
        String workerId,
        String decision,
        String code,
        String message,
        String source,
        Double score,
        Integer latencyMs,
        String note,
        String guardId
) {
}
