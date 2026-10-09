package ru.proxodnaya.access.features.pass.command.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.pass.api.WorkerStatusProjection;
import ru.proxodnaya.access.features.pass.command.repository.WorkerStateRepository;
import ru.proxodnaya.access.features.pass.model.Presence;
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
     * Направление прохода сейчас, под блокировкой строки рабочего до конца транзакции (anti-passback, FR-08).
     * Правило то же, что при скане ({@link Presence#nextDirection}); блокировка не даёт двум киоскам одновременно
     * отметить один и тот же вход или выход.
     *
     * @param workerId рабочий
     * @param moment   момент прохода
     * @param ttl      сколько считаем человека внутри после входа
     * @return IN или OUT
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Direction lockDirection(String workerId, Instant moment, Duration ttl) {
        return repository.lockPresence(workerId, moment).nextDirection(moment, ttl);
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
