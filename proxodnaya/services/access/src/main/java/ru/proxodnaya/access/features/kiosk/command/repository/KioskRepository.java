package ru.proxodnaya.access.features.kiosk.command.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.features.kiosk.model.Kiosk;
import ru.proxodnaya.access.features.kiosk.model.KioskPatch;
import ru.proxodnaya.access.jooq.Tables;
import ru.proxodnaya.access.jooq.tables.KioskNonces;
import ru.proxodnaya.access.jooq.tables.Kiosks;

/**
 * Терминалы и одноразовые nonce их запросов.
 */
@Repository
public class KioskRepository {

    private static final Kiosks KIOSKS = Tables.KIOSKS;
    private static final KioskNonces NONCES = Tables.KIOSK_NONCES;

    private final DSLContext dsl;

    public KioskRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Новый несопряжённый терминал.
     *
     * @param id        id
     * @param publicKey ключ
     * @param pairCode  код сопряжения
     * @param moment    момент
     */
    public void insert(UUID id, String publicKey, String pairCode, Instant moment) {
        dsl.insertInto(KIOSKS)
                .set(KIOSKS.ID, id)
                .set(KIOSKS.PUBLIC_KEY, publicKey)
                .set(KIOSKS.PAIR_CODE, pairCode)
                .set(KIOSKS.CREATED_AT, utc(moment))
                .set(KIOSKS.LAST_SEEN, utc(moment))
                .execute();
    }

    /**
     * Терминал по id.
     *
     * @param id id
     * @return терминал
     */
    public Optional<Kiosk> find(UUID id) {
        return dsl.selectFrom(KIOSKS).where(KIOSKS.ID.eq(id)).fetchOptional(KioskRepository::toKiosk);
    }

    /**
     * Есть ли код сопряжения.
     *
     * @param pairCode код
     * @return {@code true}, если занят
     */
    public boolean codeTaken(String pairCode) {
        return dsl.fetchExists(KIOSKS, KIOSKS.PAIR_CODE.eq(pairCode));
    }

    /**
     * Несопряжённый терминал по коду.
     *
     * @param pairCode код
     * @return терминал
     */
    public Optional<Kiosk> findByCode(String pairCode) {
        return dsl.selectFrom(KIOSKS).where(KIOSKS.PAIR_CODE.eq(pairCode)).and(KIOSKS.PAIRED_AT.isNull())
                .fetchOptional(KioskRepository::toKiosk);
    }

    /**
     * Сопряжённые и недавно выходившие на связь несопряжённые.
     *
     * @param waitingSince несопряжённые с пульсом позже этого момента
     * @return терминалы, новые сверху
     */
    public List<Kiosk> list(Instant waitingSince) {
        return dsl.selectFrom(KIOSKS)
                .where(KIOSKS.PAIRED_AT.isNotNull().or(KIOSKS.LAST_SEEN.ge(utc(waitingSince))))
                .orderBy(KIOSKS.CREATED_AT.desc())
                .fetch(KioskRepository::toKiosk);
    }

    /**
     * Сопрягает терминал, код сгорает.
     *
     * @param id       терминал
     * @param patch    название, проходная, логика
     * @param pairedBy кто привязал
     * @param moment   момент
     * @return {@code true}, если терминал был свободен
     */
    public boolean pair(UUID id, KioskPatch patch, String pairedBy, Instant moment) {
        return dsl.update(KIOSKS)
                .set(KIOSKS.NAME, patch.name())
                .set(KIOSKS.CHECKPOINT_ID, patch.checkpointId())
                .set(KIOSKS.MODE, patch.mode())
                .set(KIOSKS.OFFLINE_POLICY, patch.offlinePolicy())
                .set(KIOSKS.PAIRED_AT, utc(moment))
                .set(KIOSKS.PAIRED_BY, pairedBy)
                .setNull(KIOSKS.PAIR_CODE)
                .where(KIOSKS.ID.eq(id)).and(KIOSKS.PAIRED_AT.isNull())
                .execute() == 1;
    }

    /**
     * Меняет название, проходную и логику; {@code null} — без изменений.
     *
     * @param id    терминал
     * @param patch изменения
     * @return {@code true}, если терминал есть
     */
    public boolean update(UUID id, KioskPatch patch) {
        return dsl.update(KIOSKS)
                .set(KIOSKS.NAME, DSL.coalesce(DSL.val(patch.name(), KIOSKS.NAME), KIOSKS.NAME))
                .set(KIOSKS.CHECKPOINT_ID, DSL.coalesce(DSL.val(patch.checkpointId(), KIOSKS.CHECKPOINT_ID), KIOSKS.CHECKPOINT_ID))
                .set(KIOSKS.MODE, DSL.coalesce(DSL.val(patch.mode(), KIOSKS.MODE), KIOSKS.MODE))
                .set(KIOSKS.OFFLINE_POLICY, DSL.coalesce(DSL.val(patch.offlinePolicy(), KIOSKS.OFFLINE_POLICY), KIOSKS.OFFLINE_POLICY))
                .where(KIOSKS.ID.eq(id))
                .execute() == 1;
    }

    /**
     * Отвязывает терминал: новый код сопряжения, ключ остаётся.
     *
     * @param id       терминал
     * @param pairCode новый код
     * @return {@code true}, если терминал есть
     */
    public boolean unpair(UUID id, String pairCode) {
        return dsl.update(KIOSKS)
                .setNull(KIOSKS.PAIRED_AT)
                .setNull(KIOSKS.PAIRED_BY)
                .setNull(KIOSKS.CHECKPOINT_ID)
                .set(KIOSKS.PAIR_CODE, pairCode)
                .where(KIOSKS.ID.eq(id))
                .execute() == 1;
    }

    /**
     * Пульс терминала.
     *
     * @param id     терминал
     * @param moment момент
     */
    public void touch(UUID id, Instant moment) {
        dsl.update(KIOSKS).set(KIOSKS.LAST_SEEN, utc(moment)).where(KIOSKS.ID.eq(id)).execute();
    }

    /**
     * Гасит nonce запроса; заодно убирает просроченные.
     *
     * @param id        терминал
     * @param nonce     nonce
     * @param expiresAt до какого момента хранить
     * @param now       сейчас
     * @return {@code true}, если nonce новый
     */
    public boolean useNonce(UUID id, String nonce, Instant expiresAt, Instant now) {
        dsl.deleteFrom(NONCES).where(NONCES.KIOSK_ID.eq(id)).and(NONCES.EXPIRES_AT.lt(utc(now))).execute();
        return dsl.insertInto(NONCES)
                .set(NONCES.KIOSK_ID, id)
                .set(NONCES.NONCE, nonce)
                .set(NONCES.EXPIRES_AT, utc(expiresAt))
                .onConflictDoNothing()
                .execute() == 1;
    }

    private static Kiosk toKiosk(Record row) {
        return new Kiosk(row.get(KIOSKS.ID), row.get(KIOSKS.PUBLIC_KEY), row.get(KIOSKS.PAIR_CODE), row.get(KIOSKS.NAME),
                row.get(KIOSKS.CHECKPOINT_ID), row.get(KIOSKS.MODE), row.get(KIOSKS.OFFLINE_POLICY), instant(row.get(KIOSKS.CREATED_AT)),
                instant(row.get(KIOSKS.PAIRED_AT)), instant(row.get(KIOSKS.LAST_SEEN)));
    }

    private static OffsetDateTime utc(Instant moment) {
        return moment.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
