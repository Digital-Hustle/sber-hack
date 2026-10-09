package ru.proxodnaya.people.features.staff.query.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.staff.model.AccessEntry;
import ru.proxodnaya.people.features.staff.model.StaffFilter;
import ru.proxodnaya.people.features.staff.model.StaffPage;
import ru.proxodnaya.people.features.staff.model.StaffStatus;
import ru.proxodnaya.people.features.staff.model.StaffUser;
import ru.proxodnaya.people.features.staff.query.repository.StaffReadRepository;
import ru.proxodnaya.people.shared.Cursor;

/**
 * Чтение персонала, текущей сессии и журнала доступа.
 */
@Service
public class StaffQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final StaffReadRepository repository;
    private final Clock clock;

    public StaffQueryService(StaffReadRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Текущий сотрудник по сессии: закрытая сессия или отключённый сотрудник — сразу 401.
     *
     * @param sessionId claim {@code sid} или {@code null}
     * @param staffId   {@code sub}
     * @return сотрудник
     * @throws DomainException SESSION_EXPIRED
     */
    @Transactional(readOnly = true)
    public StaffUser me(UUID sessionId, UUID staffId) {
        if (sessionId == null || staffId == null || !repository.sessionActive(sessionId, staffId, clock.instant())) {
            throw expired();
        }
        return repository.find(staffId).filter(user -> user.status() == StaffStatus.ACTIVE).orElseThrow(StaffQueryService::expired);
    }

    /**
     * Сотрудник по id.
     *
     * @param id сотрудник
     * @return сотрудник
     * @throws DomainException NOT_FOUND
     */
    @Transactional(readOnly = true)
    public StaffUser get(UUID id) {
        return repository.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Сотрудник не найден"));
    }

    /**
     * Страница персонала.
     *
     * @param filter фильтр
     * @param cursor курсор
     * @param limit  размер
     * @return страница
     */
    @Transactional(readOnly = true)
    public StaffPage<StaffUser> page(StaffFilter filter, String cursor, Integer limit) {
        int size = size(limit);
        List<StaffUser> rows = repository.page(filter, Cursor.decode(cursor), size + 1);
        boolean more = rows.size() > size;
        List<StaffUser> items = more ? rows.subList(0, size) : rows;
        return new StaffPage<>(items, more ? Cursor.encode(items.getLast().id()) : null, repository.count(filter));
    }

    /**
     * Страница журнала доступа.
     *
     * @param cursor курсор
     * @param limit  размер
     * @return страница
     */
    @Transactional(readOnly = true)
    public StaffPage<AccessEntry> log(String cursor, Integer limit) {
        int size = size(limit);
        List<AccessEntry> rows = repository.log(Cursor.decode(cursor), size + 1);
        boolean more = rows.size() > size;
        List<AccessEntry> items = more ? rows.subList(0, size) : rows;
        return new StaffPage<>(items, more ? Cursor.encode(items.getLast().id()) : null, repository.logCount());
    }

    private static int size(Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "limit — от 1 до " + MAX_LIMIT);
        }
        return size;
    }

    private static DomainException expired() {
        return new DomainException(ErrorCode.SESSION_EXPIRED, "Сессия завершена — войдите снова");
    }
}
