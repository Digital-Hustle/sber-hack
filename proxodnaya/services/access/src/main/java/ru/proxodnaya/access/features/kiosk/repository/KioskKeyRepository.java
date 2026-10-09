package ru.proxodnaya.access.features.kiosk.repository;

import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.features.kiosk.model.KioskKey;
import ru.proxodnaya.access.jooq.Tables;
import ru.proxodnaya.access.jooq.tables.records.KioskKeysRecord;

/**
 * Чтение публичных ключей киосков для проверки подписи (FR-64).
 */
@Repository
public class KioskKeyRepository {

    private static final ru.proxodnaya.access.jooq.tables.KioskKeys KIOSK_KEYS = Tables.KIOSK_KEYS;

    private final DSLContext dsl;

    public KioskKeyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Активный ключ киоска по id.
     *
     * @param kioskId id киоска
     * @return ключ или пусто
     */
    public Optional<KioskKey> findActive(String kioskId) {
        return dsl.selectFrom(KIOSK_KEYS)
                .where(KIOSK_KEYS.KIOSK_ID.eq(kioskId).and(KIOSK_KEYS.STATUS.eq("ACTIVE")))
                .fetchOptional(this::toModel);
    }

    /**
     * Сохраняет или обновляет ключ киоска (из people.device.v1 или /auth/kiosk/pair).
     */
    public void upsert(KioskKey key) {
        dsl.insertInto(KIOSK_KEYS)
                .set(KIOSK_KEYS.KIOSK_ID, key.getKioskId())
                .set(KIOSK_KEYS.CHECKPOINT_ID, key.getCheckpointId())
                .set(KIOSK_KEYS.PUBLIC_KEY, key.getPublicKey())
                .set(KIOSK_KEYS.STATUS, key.getStatus())
                .set(KIOSK_KEYS.UPDATED_AT, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))
                .onConflict(KIOSK_KEYS.KIOSK_ID)
                .doUpdate()
                .set(KIOSK_KEYS.CHECKPOINT_ID, key.getCheckpointId())
                .set(KIOSK_KEYS.PUBLIC_KEY, key.getPublicKey())
                .set(KIOSK_KEYS.STATUS, key.getStatus())
                .set(KIOSK_KEYS.UPDATED_AT, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))
                .execute();
    }

    private KioskKey toModel(KioskKeysRecord r) {
        return KioskKey.builder()
                .kioskId(r.getKioskId())
                .checkpointId(r.getCheckpointId())
                .publicKey(r.getPublicKey())
                .status(r.getStatus())
                .build();
    }
}
