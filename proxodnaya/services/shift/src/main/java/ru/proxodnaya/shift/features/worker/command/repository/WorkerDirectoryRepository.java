package ru.proxodnaya.shift.features.worker.command.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.worker.model.WorkerCard;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * Проекция карточек рабочих. Событие старше уже применённого не перетирает данные.
 */
@Repository
public class WorkerDirectoryRepository {

    private static final ru.proxodnaya.shift.jooq.tables.Workers WORKERS = Tables.WORKERS;

    private final DSLContext dsl;

    public WorkerDirectoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Создаёт или заменяет карточку целиком.
     *
     * @param card   карточка
     * @param moment момент события
     */
    public void upsert(WorkerCard card, Instant moment) {
        OffsetDateTime stamp = moment.atOffset(ZoneOffset.UTC);
        dsl.insertInto(WORKERS)
                .set(WORKERS.ID, card.workerId())
                .set(WORKERS.FULL_NAME, card.fullName())
                .set(WORKERS.POSITION, card.position())
                .set(WORKERS.CONTRACTOR, card.contractor())
                .set(WORKERS.STATUS, card.status())
                .set(WORKERS.UPDATED_AT, stamp)
                .onConflict(WORKERS.ID)
                .doUpdate()
                .set(WORKERS.FULL_NAME, card.fullName())
                .set(WORKERS.POSITION, card.position())
                .set(WORKERS.CONTRACTOR, card.contractor())
                .set(WORKERS.STATUS, card.status())
                .set(WORKERS.UPDATED_AT, stamp)
                .where(WORKERS.UPDATED_AT.le(stamp))
                .execute();
    }

    /**
     * Меняет только статус; если карточки ещё нет — создаёт без ФИО.
     *
     * @param workerId рабочий
     * @param status   статус
     * @param moment   момент события
     */
    public void updateStatus(String workerId, String status, Instant moment) {
        OffsetDateTime stamp = moment.atOffset(ZoneOffset.UTC);
        dsl.insertInto(WORKERS)
                .set(WORKERS.ID, workerId)
                .set(WORKERS.STATUS, status)
                .set(WORKERS.UPDATED_AT, stamp)
                .onConflict(WORKERS.ID)
                .doUpdate()
                .set(WORKERS.STATUS, status)
                .set(WORKERS.UPDATED_AT, stamp)
                .where(WORKERS.UPDATED_AT.le(stamp))
                .execute();
    }
}
