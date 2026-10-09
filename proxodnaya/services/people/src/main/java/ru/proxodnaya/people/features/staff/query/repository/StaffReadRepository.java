package ru.proxodnaya.people.features.staff.query.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.staff.model.AccessAction;
import ru.proxodnaya.people.features.staff.model.AccessEntry;
import ru.proxodnaya.people.features.staff.model.StaffFilter;
import ru.proxodnaya.people.features.staff.model.StaffRole;
import ru.proxodnaya.people.features.staff.model.StaffStatus;
import ru.proxodnaya.people.features.staff.model.StaffUser;
import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.StaffAccessLog;
import ru.proxodnaya.people.jooq.tables.StaffSessions;
import ru.proxodnaya.people.jooq.tables.StaffUsers;

/**
 * Персонал, сессии, журнал доступа (чтение).
 */
@Repository
public class StaffReadRepository {

    private static final StaffUsers STAFF = Tables.STAFF_USERS;
    private static final StaffSessions SESSIONS = Tables.STAFF_SESSIONS;
    private static final StaffAccessLog LOG = Tables.STAFF_ACCESS_LOG;

    private static final String PERCENT = "%";

    private final DSLContext dsl;

    public StaffReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сотрудник по id.
     *
     * @param id сотрудник
     * @return сотрудник
     */
    public Optional<StaffUser> find(UUID id) {
        return dsl.selectFrom(STAFF).where(STAFF.ID.eq(id)).fetchOptional(StaffReadRepository::toUser);
    }

    /**
     * Сессия действует (не закрыта и не истекла).
     *
     * @param sessionId сессия
     * @param staffId   её сотрудник
     * @param now       момент
     * @return действует
     */
    public boolean sessionActive(UUID sessionId, UUID staffId, Instant now) {
        return dsl.fetchExists(SESSIONS, SESSIONS.ID.eq(sessionId).and(SESSIONS.STAFF_ID.eq(staffId))
                .and(SESSIONS.REVOKED_AT.isNull()).and(SESSIONS.EXPIRES_AT.gt(now.atOffset(ZoneOffset.UTC))));
    }

    /**
     * Страница персонала, новые сверху.
     *
     * @param filter фильтр
     * @param after  id последней строки прошлой страницы или {@code null}
     * @param limit  размер + 1
     * @return строки
     */
    public List<StaffUser> page(StaffFilter filter, UUID after, int limit) {
        Condition condition = matching(filter);
        if (after != null) {
            condition = condition.and(STAFF.ID.lt(after));
        }
        return dsl.selectFrom(STAFF).where(condition).orderBy(STAFF.ID.desc()).limit(limit).fetch(StaffReadRepository::toUser);
    }

    /**
     * Сколько под фильтром.
     *
     * @param filter фильтр
     * @return число
     */
    public long count(StaffFilter filter) {
        return dsl.fetchCount(STAFF, matching(filter));
    }

    /**
     * Страница журнала, новые сверху.
     *
     * @param after id последней записи прошлой страницы или {@code null}
     * @param limit размер + 1
     * @return записи
     */
    public List<AccessEntry> log(UUID after, int limit) {
        Condition condition = after == null ? DSL.trueCondition() : LOG.ID.lt(after);
        return dsl.selectFrom(LOG).where(condition).orderBy(LOG.ID.desc()).limit(limit)
                .fetch(row -> new AccessEntry(row.getId(), row.getTs().toInstant(), row.getById(), row.getByName(), row.getTargetId(),
                        row.getTarget(), AccessAction.valueOf(row.getAction()), row.getFromRole(), row.getToRole(), row.getCode()));
    }

    /**
     * Записей в журнале.
     *
     * @return число
     */
    public long logCount() {
        return dsl.fetchCount(LOG);
    }

    private static Condition matching(StaffFilter filter) {
        Condition condition = DSL.trueCondition();
        if (filter.query() != null && !filter.query().isBlank()) {
            String like = PERCENT + filter.query().strip().toLowerCase().replace("\\", "\\\\").replace(PERCENT, "\\%")
                    .replace("_", "\\_") + PERCENT;
            condition = condition.and(DSL.lower(STAFF.NAME).like(like, '\\').or(DSL.lower(STAFF.EMAIL).like(like, '\\')));
        }
        if (filter.role() != null) {
            condition = condition.and(STAFF.ROLE.eq(filter.role().name()));
        }
        if (filter.status() != null) {
            condition = condition.and(STAFF.STATUS.eq(filter.status().name()));
        }
        return condition;
    }

    private static StaffUser toUser(ru.proxodnaya.people.jooq.tables.records.StaffUsersRecord row) {
        OffsetDateTime seen = row.getLastSeenAt();
        return new StaffUser(row.getId(), row.getName(), row.getEmail(), StaffRole.valueOf(row.getRole()),
                StaffStatus.valueOf(row.getStatus()), seen == null ? null : seen.toInstant());
    }
}
