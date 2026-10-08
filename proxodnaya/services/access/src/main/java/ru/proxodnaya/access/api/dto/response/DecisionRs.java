package ru.proxodnaya.access.api.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Решение о проходе ({@code DecisionRs} из docs/BACKEND-CONTRACT.md). Отказ — это 200, а не HTTP-ошибка.
 *
 * @param attemptId попытка
 * @param decision  ALLOW | DENY | MANUAL | ERROR
 * @param code      код причины
 * @param message   текст для экрана
 * @param hint      что делать
 * @param worker    кто проходит, если известен
 * @param direction IN | OUT
 * @param score     близость лица
 * @param ts        время решения
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DecisionRs(
        String attemptId,
        String decision,
        String code,
        String message,
        String hint,
        WorkerRs worker,
        String direction,
        Double score,
        Instant ts
) {
}
