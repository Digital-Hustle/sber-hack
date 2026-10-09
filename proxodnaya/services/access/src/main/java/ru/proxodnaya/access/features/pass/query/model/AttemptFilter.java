package ru.proxodnaya.access.features.pass.query.model;

import java.time.Instant;

import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Фильтры журнала проходов (FR-10/31). {@code null} — без фильтра.
 *
 * @param workerId     заявленный рабочий
 * @param checkpointId проходная
 * @param decision     решение
 * @param code         код причины
 * @param source       источник записи
 * @param direction    направление
 * @param from         начало периода, включительно
 * @param until        конец периода, не включая
 */
public record AttemptFilter(
        String workerId,
        String checkpointId,
        Decision decision,
        ReasonCode code,
        AttemptSource source,
        Direction direction,
        Instant from,
        Instant until
) {

    /**
     * Только проходы одного рабочего (история на телефоне).
     *
     * @param workerId рабочий
     * @return фильтр
     */
    public static AttemptFilter ofWorker(String workerId) {
        return new AttemptFilter(workerId, null, null, null, null, null, null, null);
    }
}
