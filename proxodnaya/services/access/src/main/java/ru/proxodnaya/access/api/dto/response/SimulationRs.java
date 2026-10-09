package ru.proxodnaya.access.api.dto.response;

import java.time.Instant;

/**
 * Итог прогона симулятора.
 *
 * @param attempts записано попыток
 * @param allowed  пропусков
 * @param denied   отказов
 * @param firstTs  первая попытка
 * @param lastTs   последняя
 */
public record SimulationRs(int attempts, int allowed, int denied, Instant firstTs, Instant lastTs) {
}
