package ru.proxodnaya.access.features.pass.model;

import java.util.List;

/**
 * Что сгенерировать.
 *
 * @param checkpointId проходная (её зона — из people)
 * @param workerIds    рабочие
 * @param days         за сколько прошедших дней, сегодняшний не трогаем
 * @param denyRate     доля дней с отказом перед входом, 0..1
 * @param seed         зерно случайности (одинаковое — одинаковая история) или {@code null}
 */
public record SimulationRequest(String checkpointId, List<String> workerIds, int days, double denyRate, Long seed) {
}
