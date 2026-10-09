package ru.proxodnaya.people.features.worker.query.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.worker.model.Permit;
import ru.proxodnaya.people.features.worker.model.PermitType;
import ru.proxodnaya.people.features.worker.model.Worker;
import ru.proxodnaya.people.features.worker.model.WorkerFilter;
import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.Workers;
import ru.proxodnaya.people.shared.WorkerStatus;

/**
 * Чтение рабочих: поиск, фильтр и курсор — на стороне БД.
 */
@Repository
public class WorkerReadRepository {

    private static final Workers WORKERS = Tables.WORKERS;
    private static final String WILDCARD = "%";
    private static final char ESCAPE = '\\';

    private final DSLContext dsl;

    public WorkerReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Рабочий по id (удалённые не видны).
     *
     * @param id id
     * @return рабочий
     */
    public Optional<Worker> find(UUID id) {
        return dsl.selectFrom(WORKERS)
                .where(WORKERS.ID.eq(id).and(WORKERS.STATUS.ne(WorkerStatus.DELETED.name())))
                .fetchOptional(WorkerReadRepository::toWorker);
    }

    /**
     * Страница, новые сверху.
     *
     * @param query  строка поиска или {@code null}
     * @param filter фильтр
     * @param after  id последней строки прошлой страницы или {@code null}
     * @param limit  размер страницы + 1 (чтобы понять, есть ли следующая)
     * @return строки
     */
    public List<Worker> page(String query, WorkerFilter filter, UUID after, int limit) {
        Condition condition = matching(query, filter);
        if (after != null) {
            condition = condition.and(WORKERS.ID.lt(after));
        }
        return dsl.selectFrom(WORKERS).where(condition).orderBy(WORKERS.ID.desc()).limit(limit)
                .fetch(WorkerReadRepository::toWorker);
    }

    /**
     * Сколько строк под фильтром.
     *
     * @param query  строка поиска
     * @param filter фильтр
     * @return количество
     */
    public long count(String query, WorkerFilter filter) {
        return dsl.fetchCount(WORKERS, matching(query, filter));
    }

    /**
     * Зоны допуска рабочих.
     *
     * @param workerIds рабочие
     * @return рабочий → зоны
     */
    public Map<UUID, Set<UUID>> zonesOf(Collection<UUID> workerIds) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        dsl.select(Tables.WORKER_ZONES.WORKER_ID, Tables.WORKER_ZONES.ZONE_ID).from(Tables.WORKER_ZONES)
                .where(Tables.WORKER_ZONES.WORKER_ID.in(workerIds))
                .forEach(row -> result.computeIfAbsent(row.value1(), key -> new HashSet<>()).add(row.value2()));
        return result;
    }

    /**
     * Допуски со сроком.
     *
     * @param workerIds рабочие
     * @return рабочий → допуски по виду
     */
    public Map<UUID, List<Permit>> permitsOf(Collection<UUID> workerIds) {
        Map<UUID, List<Permit>> result = new HashMap<>();
        dsl.select(Tables.PERMITS.WORKER_ID, Tables.PERMITS.TYPE, Tables.PERMITS.VALID_UNTIL).from(Tables.PERMITS)
                .where(Tables.PERMITS.WORKER_ID.in(workerIds)).orderBy(Tables.PERMITS.TYPE)
                .forEach(row -> result.computeIfAbsent(row.value1(), key -> new ArrayList<>())
                        .add(new Permit(PermitType.valueOf(row.value2()), row.value3())));
        return result;
    }

    private static Condition matching(String query, WorkerFilter filter) {
        Condition condition = switch (filter) {
            case ALL -> WORKERS.STATUS.ne(WorkerStatus.DELETED.name());
            case ACTIVE -> WORKERS.STATUS.eq(WorkerStatus.ACTIVE.name());
            case BLOCKED -> WORKERS.STATUS.eq(WorkerStatus.BLOCKED.name());
        };
        if (query != null && !query.isBlank()) {
            String pattern = WILDCARD + escape(query.strip()) + WILDCARD;
            condition = condition.and(DSL.or(
                    WORKERS.FULL_NAME.likeIgnoreCase(pattern, ESCAPE),
                    WORKERS.POSITION.likeIgnoreCase(pattern, ESCAPE),
                    WORKERS.CONTRACTOR.likeIgnoreCase(pattern, ESCAPE)));
        }
        return condition;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char symbol : value.toCharArray()) {
            if (symbol == ESCAPE || symbol == '%' || symbol == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(symbol);
        }
        return escaped.toString();
    }

    private static Worker toWorker(Record row) {
        return new Worker(row.get(WORKERS.ID), row.get(WORKERS.FULL_NAME), row.get(WORKERS.POSITION),
                row.get(WORKERS.CONTRACTOR), WorkerStatus.valueOf(row.get(WORKERS.STATUS)),
                row.get(WORKERS.CREATED_AT).toInstant(), row.get(WORKERS.UPDATED_AT).toInstant());
    }
}
