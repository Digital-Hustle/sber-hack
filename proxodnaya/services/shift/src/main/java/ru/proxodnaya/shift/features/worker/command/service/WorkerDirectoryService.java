package ru.proxodnaya.shift.features.worker.command.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.shift.features.worker.command.repository.WorkerDirectoryRepository;
import ru.proxodnaya.shift.features.worker.model.WorkerCard;

/**
 * Применяет события {@code people.worker.v1} к проекции карточек (нужна табелю, SH-09).
 */
@Service
@Transactional
public class WorkerDirectoryService {

    private static final String DELETED = "DELETED";
    private static final String BLOCKED = "BLOCKED";

    private final WorkerDirectoryRepository repository;

    public WorkerDirectoryService(WorkerDirectoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Создание или правка: карточка заменяется целиком.
     *
     * @param card   карточка из события
     * @param moment момент события
     */
    public void save(WorkerCard card, Instant moment) {
        repository.upsert(card, moment);
    }

    /**
     * Блокировка.
     *
     * @param workerId рабочий
     * @param moment   момент события
     */
    public void block(String workerId, Instant moment) {
        repository.updateStatus(workerId, BLOCKED, moment);
    }

    /**
     * Мягкое удаление: ФИО остаётся, чтобы прошлые табели читались.
     *
     * @param workerId рабочий
     * @param moment   момент события
     */
    public void delete(String workerId, Instant moment) {
        repository.updateStatus(workerId, DELETED, moment);
    }
}
