package ru.proxodnaya.access.features.pass.command.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.pass.api.WorkerStatusProjection;
import ru.proxodnaya.access.features.pass.command.repository.WorkerStateRepository;
import ru.proxodnaya.access.shared.Direction;

/**
 * Присутствие на объекте и проекция статуса рабочего.
 */
@Service
public class PresenceService implements WorkerStatusProjection {

    private final WorkerStateRepository repository;

    public PresenceService(WorkerStateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void updateStatus(String workerId, String status, Instant moment) {
        repository.upsertStatus(workerId, status, moment);
    }

    /**
     * Отмечает проход. Только в транзакции решения.
     *
     * @param workerId  рабочий
     * @param direction направление
     * @param moment        время
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(String workerId, Direction direction, Instant moment) {
        if (direction == Direction.IN) {
            repository.markIn(workerId, moment);
        } else {
            repository.markOut(workerId, moment);
        }
    }
}
