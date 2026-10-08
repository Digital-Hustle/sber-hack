package ru.proxodnaya.access.features.pass.api;

import java.time.Instant;

/**
 * Проекция статуса рабочего из {@code people.worker.v1}.
 */
public interface WorkerStatusProjection {

    /**
     * Обновляет статус.
     *
     * @param workerId рабочий
     * @param status   ACTIVE | BLOCKED | DELETED
     * @param moment       момент события
     */
    void updateStatus(String workerId, String status, Instant moment);
}
