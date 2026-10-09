package ru.proxodnaya.people.features.device.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.Invites;

/**
 * Инвайты.
 */
@Repository
public class InviteRepository {

    private static final Invites INVITES = Tables.INVITES;

    private final DSLContext dsl;

    public InviteRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Отзывает неиспользованные инвайты рабочего (действует только последний).
     *
     * @param workerId рабочий
     * @param moment   момент
     */
    public void revokeOpen(UUID workerId, Instant moment) {
        dsl.update(INVITES).set(INVITES.REVOKED_AT, moment.atOffset(ZoneOffset.UTC))
                .where(INVITES.WORKER_ID.eq(workerId).and(INVITES.USED_AT.isNull()).and(INVITES.REVOKED_AT.isNull()))
                .execute();
    }

    /**
     * Сохраняет инвайт; {@code false} — такой хэш уже есть (коллизия кода).
     *
     * @param workerId  рабочий
     * @param codeHash  HMAC кода
     * @param createdAt создан
     * @param expiresAt действует до
     * @return вставлен ли
     */
    public boolean insert(UUID workerId, String codeHash, Instant createdAt, Instant expiresAt) {
        return dsl.insertInto(INVITES)
                .set(INVITES.ID, UUID.randomUUID())
                .set(INVITES.WORKER_ID, workerId)
                .set(INVITES.CODE_HASH, codeHash)
                .set(INVITES.CREATED_AT, createdAt.atOffset(ZoneOffset.UTC))
                .set(INVITES.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
                .onConflict(INVITES.CODE_HASH).doNothing()
                .execute() == 1;
    }

    /**
     * Инвайт по хэшу кода, строка заблокирована (одноразовость при гонке).
     *
     * @param codeHash HMAC кода
     * @return инвайт
     */
    public Optional<InviteRow> lock(String codeHash) {
        return dsl.selectFrom(INVITES).where(INVITES.CODE_HASH.eq(codeHash)).forUpdate()
                .fetchOptional(row -> new InviteRow(row.getId(), row.getWorkerId(), row.getExpiresAt().toInstant(),
                        row.getUsedAt() != null || row.getRevokedAt() != null));
    }

    /**
     * Гасит инвайт.
     *
     * @param id     инвайт
     * @param moment момент
     */
    public void markUsed(UUID id, Instant moment) {
        dsl.update(INVITES).set(INVITES.USED_AT, moment.atOffset(ZoneOffset.UTC)).where(INVITES.ID.eq(id)).execute();
    }

    /**
     * Строка инвайта.
     *
     * @param id        id
     * @param workerId  рабочий
     * @param expiresAt действует до
     * @param closed    использован или отозван
     */
    public record InviteRow(UUID id, UUID workerId, Instant expiresAt, boolean closed) {
    }
}
