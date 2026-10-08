package ru.proxodnaya.access.features.pass.model;

import java.time.Instant;
import java.util.UUID;

import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Итог попытки для киоска.
 *
 * @param attemptId попытка
 * @param decision  решение
 * @param code      причина
 * @param worker    кто проходит, если известен
 * @param direction направление
 * @param score     близость лица
 * @param ts        время решения
 */
public record PassOutcome(
        UUID attemptId,
        Decision decision,
        ReasonCode code,
        WorkerPreview worker,
        Direction direction,
        Double score,
        Instant ts
) {
}
