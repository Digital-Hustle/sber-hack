package ru.proxodnaya.people.features.worker.query.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.worker.model.Permit;
import ru.proxodnaya.people.features.worker.model.Worker;
import ru.proxodnaya.people.features.worker.model.WorkerFilter;
import ru.proxodnaya.people.features.worker.model.WorkerPage;
import ru.proxodnaya.people.features.worker.model.WorkerView;
import ru.proxodnaya.people.features.worker.query.repository.WorkerReadRepository;
import ru.proxodnaya.people.shared.Cursor;

/**
 * Чтение рабочих для кабинета.
 */
@Service
public class WorkerQueryService {

    /** Размер страницы по умолчанию. */
    public static final int DEFAULT_LIMIT = 50;
    /** Максимальный размер страницы (PAGE_MAX в BACKEND-CONTRACT). */
    public static final int MAX_LIMIT = 200;

    private final WorkerReadRepository repository;

    public WorkerQueryService(WorkerReadRepository repository) {
        this.repository = repository;
    }

    /**
     * Рабочий по id.
     *
     * @param id id
     * @return рабочий
     * @throws DomainException WORKER_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public WorkerView get(UUID id) {
        Worker worker = repository.find(id)
                .orElseThrow(() -> new DomainException(ErrorCode.WORKER_NOT_FOUND, "Рабочий не найден"));
        return views(List.of(worker)).getFirst();
    }

    /**
     * Страница рабочих.
     *
     * @param query  поиск по ФИО, должности, подрядчику
     * @param filter {@code all|active|blocked}
     * @param cursor курсор или {@code null}
     * @param limit  размер страницы (1..200, по умолчанию 50)
     * @return страница
     * @throws DomainException VALIDATION_ERROR для неверных фильтра, курсора или limit
     */
    @Transactional(readOnly = true)
    public WorkerPage page(String query, String filter, String cursor, Integer limit) {
        WorkerFilter parsed = WorkerFilter.parse(filter);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "limit — от 1 до " + MAX_LIMIT);
        }
        List<Worker> rows = repository.page(query, parsed, Cursor.decode(cursor), size + 1);
        boolean more = rows.size() > size;
        List<Worker> items = more ? rows.subList(0, size) : rows;
        String next = more ? Cursor.encode(items.getLast().id()) : null;
        return new WorkerPage(views(items), next, repository.count(query, parsed));
    }

    private List<WorkerView> views(List<Worker> workers) {
        if (workers.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = workers.stream().map(Worker::id).toList();
        Map<UUID, Set<UUID>> zones = repository.zonesOf(ids);
        Map<UUID, List<Permit>> permits = repository.permitsOf(ids);
        Map<UUID, Instant> faces = repository.facesOf(ids);
        return workers.stream()
                .map(worker -> new WorkerView(worker, Set.copyOf(zones.getOrDefault(worker.id(), Set.of())),
                        List.copyOf(permits.getOrDefault(worker.id(), List.of())), faces.get(worker.id())))
                .toList();
    }
}
