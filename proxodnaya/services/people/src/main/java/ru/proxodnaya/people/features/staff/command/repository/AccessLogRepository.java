package ru.proxodnaya.people.features.staff.command.repository;

import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.staff.model.AccessEntry;
import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.StaffAccessLog;

/**
 * Журнал доступа (запись).
 */
@Repository
public class AccessLogRepository {

    private static final StaffAccessLog LOG = Tables.STAFF_ACCESS_LOG;

    private final DSLContext dsl;

    public AccessLogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Добавляет запись.
     *
     * @param entry запись
     */
    public void insert(AccessEntry entry) {
        dsl.insertInto(LOG)
                .set(LOG.ID, entry.id())
                .set(LOG.TS, entry.ts().atOffset(ZoneOffset.UTC))
                .set(LOG.BY_ID, entry.byId())
                .set(LOG.BY_NAME, entry.byName())
                .set(LOG.TARGET_ID, entry.targetId())
                .set(LOG.TARGET, entry.target())
                .set(LOG.ACTION, entry.action().name())
                .set(LOG.FROM_ROLE, entry.fromRole())
                .set(LOG.TO_ROLE, entry.toRole())
                .set(LOG.CODE, entry.code())
                .execute();
    }
}
