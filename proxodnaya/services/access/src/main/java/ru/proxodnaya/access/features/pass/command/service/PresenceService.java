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
     * Заполнена ли зона (FR-08, ACC-17): под блокировкой зоны до конца транзакции считает, сколько человек внутри,
     * кроме входящего. Блокировка держится до коммита, поэтому параллельный вход увидит уже записанный.
     *
     * @param zoneId   зона
     * @param capacity вместимость
     * @param workerId входящий
     * @param moment   момент прохода
     * @param ttl      сколько считаем человека внутри после входа
     * @return {@code true}, если мест нет
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean zoneFull(String zoneId, int capacity, String workerId, Instant moment, Duration ttl) {
        repository.lockZone(zoneId);
        return repository.insideZone(zoneId, moment.minus(ttl), workerId) >= capacity;
    }

    /**
     * Отмечает проход. Только в транзакции решения.
     *
     * @param workerId  рабочий
     * @param zoneId    зона прохода
     * @param direction направление
     * @param moment    время
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(String workerId, String zoneId, Direction direction, Instant moment) {
        if (direction == Direction.IN) {
            repository.markIn(workerId, zoneId, moment);
        } else {
            repository.markOut(workerId, moment);
        }
    }
}
