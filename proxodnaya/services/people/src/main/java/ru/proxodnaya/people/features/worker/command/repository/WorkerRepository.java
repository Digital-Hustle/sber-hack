package ru.proxodnaya.people.features.worker.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.worker.model.Worker;
import ru.proxodnaya.people.features.worker.model.WorkerDraft;
import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.Workers;
import ru.proxodnaya.people.shared.WorkerStatus;

/**
 * Запись рабочих.
 */
@Repository
public class WorkerRepository {

    private static final Workers WORKERS = Tables.WORKERS;

    private final DSLContext dsl;

    public WorkerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Вставляет рабочего.
     *
     * @param worker рабочий
     */
    public void insert(Worker worker) {
        dsl.insertInto(WORKERS)
                .set(WORKERS.ID, worker.id())
                .set(WORKERS.FULL_NAME, worker.fullName())
                .set(WORKERS.POSITION, worker.position())
                .set(WORKERS.CONTRACTOR, worker.contractor())
                .set(WORKERS.STATUS, worker.status().name())
                .set(WORKERS.CREATED_AT, worker.createdAt().atOffset(ZoneOffset.UTC))
                .set(WORKERS.UPDATED_AT, worker.updatedAt().atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Блокирует строку рабочего до конца транзакции (не удалённого).
     *
     * @param id id
     * @return рабочий
     */
    public Optional<Worker> lock(UUID id) {
        return dsl.selectFrom(WORKERS)
                .where(WORKERS.ID.eq(id).and(WORKERS.STATUS.ne(WorkerStatus.DELETED.name())))
                .forUpdate()
                .fetchOptional(WorkerRepository::toWorker);
    }

    /**
     * Сохраняет изменённые поля и статус.
     *
     * @param worker новое состояние
     */
    public void update(Worker worker) {
        dsl.update(WORKERS)
                .set(WORKERS.FULL_NAME, worker.fullName())
                .set(WORKERS.POSITION, worker.position())
                .set(WORKERS.CONTRACTOR, worker.contractor())
                .set(WORKERS.STATUS, worker.status().name())
                .set(WORKERS.UPDATED_AT, worker.updatedAt().atOffset(ZoneOffset.UTC))
                .where(WORKERS.ID.eq(worker.id()))
                .execute();
    }

    /**
     * Применяет черновик к рабочему.
     *
     * @param worker текущее состояние
     * @param draft  изменения ({@code null} — не трогать)
     * @param moment момент изменения
     * @return новое состояние
     */
    public static Worker apply(Worker worker, WorkerDraft draft, Instant moment) {
        return new Worker(worker.id(),
                draft.fullName() == null ? worker.fullName() : draft.fullName(),
                draft.position() == null ? worker.position() : draft.position(),
                draft.contractor() == null ? worker.contractor() : draft.contractor(),
                worker.status(), worker.createdAt(), moment);
    }

    static Worker toWorker(Record row) {
        return new Worker(row.get(WORKERS.ID), row.get(WORKERS.FULL_NAME), row.get(WORKERS.POSITION),
                row.get(WORKERS.CONTRACTOR), WorkerStatus.valueOf(row.get(WORKERS.STATUS)),
                row.get(WORKERS.CREATED_AT).toInstant(), row.get(WORKERS.UPDATED_AT).toInstant());
    }
}
