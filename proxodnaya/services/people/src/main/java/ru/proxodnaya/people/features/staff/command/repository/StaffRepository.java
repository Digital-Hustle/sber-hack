package ru.proxodnaya.people.features.staff.command.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.staff.model.StaffRole;
import ru.proxodnaya.people.features.staff.model.StaffStatus;
import ru.proxodnaya.people.features.staff.model.StaffUser;
import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.StaffUsers;

/**
 * Персонал (запись).
 */
@Repository
public class StaffRepository {

    private static final StaffUsers STAFF = Tables.STAFF_USERS;

    private final DSLContext dsl;

    public StaffRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сериализует изменения ролей и статусов: проверка «последний администратор» не проходит гонку.
     */
    public void lockAdmins() {
        dsl.execute("select pg_advisory_xact_lock(hashtext('staff:admins'))");
    }

    /**
     * Активных администраторов.
     *
     * @return сколько
     */
    public int activeAdmins() {
        return dsl.fetchCount(STAFF, STAFF.ROLE.eq(StaffRole.ADMIN.name()).and(STAFF.STATUS.eq(StaffStatus.ACTIVE.name())));
    }

    /**
     * Есть ли хоть один сотрудник.
     *
     * @return есть
     */
    public boolean any() {
        return dsl.fetchExists(STAFF);
    }

    /**
     * Сотрудник по почте (без учёта регистра), строка заблокирована.
     *
     * @param email почта
     * @return сотрудник
     */
    public Optional<StaffUser> lockByEmail(String email) {
        return dsl.selectFrom(STAFF).where(DSL.lower(STAFF.EMAIL).eq(email.toLowerCase())).forUpdate()
                .fetchOptional(StaffRepository::toUser);
    }

    /**
     * Сотрудник по id, строка заблокирована.
     *
     * @param id сотрудник
     * @return сотрудник
     */
    public Optional<StaffUser> lock(UUID id) {
        return dsl.selectFrom(STAFF).where(STAFF.ID.eq(id)).forUpdate().fetchOptional(StaffRepository::toUser);
    }

    /**
     * Новый сотрудник; {@code false} — почта занята.
     *
     * @param user   сотрудник
     * @param moment момент
     * @return вставлен ли
     */
    public boolean insert(StaffUser user, Instant moment) {
        return dsl.insertInto(STAFF)
                .set(STAFF.ID, user.id())
                .set(STAFF.NAME, user.name())
                .set(STAFF.EMAIL, user.email())
                .set(STAFF.ROLE, user.role().name())
                .set(STAFF.STATUS, user.status().name())
                .set(STAFF.CREATED_AT, moment.atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute() == 1;
    }

    /**
     * Меняет роль.
     *
     * @param id   сотрудник
     * @param role роль
     */
    public void setRole(UUID id, StaffRole role) {
        dsl.update(STAFF).set(STAFF.ROLE, role.name()).where(STAFF.ID.eq(id)).execute();
    }

    /**
     * Меняет статус.
     *
     * @param id     сотрудник
     * @param status статус
     */
    public void setStatus(UUID id, StaffStatus status) {
        dsl.update(STAFF).set(STAFF.STATUS, status.name()).where(STAFF.ID.eq(id)).execute();
    }

    /**
     * Отмечает активность.
     *
     * @param id     сотрудник
     * @param moment момент
     */
    public void touch(UUID id, Instant moment) {
        dsl.update(STAFF).set(STAFF.LAST_SEEN_AT, moment.atOffset(ZoneOffset.UTC)).where(STAFF.ID.eq(id)).execute();
    }

    /**
     * Строка → сотрудник.
     *
     * @param row строка staff_users
     * @return сотрудник
     */
    public static StaffUser toUser(Record row) {
        OffsetDateTime seen = row.get(Tables.STAFF_USERS.LAST_SEEN_AT);
        return new StaffUser(row.get(Tables.STAFF_USERS.ID), row.get(Tables.STAFF_USERS.NAME), row.get(Tables.STAFF_USERS.EMAIL),
                StaffRole.valueOf(row.get(Tables.STAFF_USERS.ROLE)), StaffStatus.valueOf(row.get(Tables.STAFF_USERS.STATUS)),
                seen == null ? null : seen.toInstant());
    }
}
