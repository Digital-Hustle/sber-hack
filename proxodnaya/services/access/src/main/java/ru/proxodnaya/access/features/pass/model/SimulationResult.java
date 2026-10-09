package ru.proxodnaya.access.features.pass.model;

import java.time.Instant;

/**
 * Итог прогона.
 *
 * @param attempts записано попыток
 * @param allowed  из них пропусков
 * @param denied   отказов
 * @param firstTs  первая попытка
 * @param lastTs   последняя
 */
public record SimulationResult(int attempts, int allowed, int denied, Instant firstTs, Instant lastTs) {
}
