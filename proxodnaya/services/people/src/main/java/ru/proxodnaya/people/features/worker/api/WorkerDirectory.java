package ru.proxodnaya.people.features.worker.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Рабочие для других фич people (устройства, инвайты).
 */
public interface WorkerDirectory {

    /**
     * Рабочий (не удалённый), строка заблокирована до конца транзакции.
     *
     * @param id рабочий
     * @return рабочий
     */
    Optional<WorkerCard> lock(UUID id);
}
