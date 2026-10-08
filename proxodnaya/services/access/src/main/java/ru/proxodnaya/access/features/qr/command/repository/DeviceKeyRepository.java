package ru.proxodnaya.access.features.qr.command.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.features.qr.command.model.DeviceKey;
import ru.proxodnaya.access.jooq.Tables;

/**
 * Проекция ключей устройств.
 */
@Repository
public class DeviceKeyRepository {

    private static final ru.proxodnaya.access.jooq.tables.DeviceKeys DEVICE_KEYS = Tables.DEVICE_KEYS;

    private static final String REVOKED = "REVOKED";
    private static final String ACTIVE = "ACTIVE";

    private final DSLContext dsl;

    public DeviceKeyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Ищет ключ устройства.
     *
     * @param deviceId устройство
     * @return ключ или пусто
     */
    public Optional<DeviceKey> find(String deviceId) {
        return dsl.select(DEVICE_KEYS.DEVICE_ID, DEVICE_KEYS.WORKER_ID, DEVICE_KEYS.PUBLIC_KEY, DEVICE_KEYS.STATUS)
                .from(DEVICE_KEYS)
                .where(DEVICE_KEYS.DEVICE_ID.eq(deviceId))
                .fetchOptional(row -> new DeviceKey(row.value1(), row.value2(), row.value3(), REVOKED.equals(row.value4())));
    }

    /**
     * Привязывает устройство (или обновляет ключ), если событие новее сохранённого.
     *
     * @param deviceId устройство
     * @param workerId владелец
     * @param spki     ключ SPKI DER
     * @param moment       момент события
     */
    public void upsert(String deviceId, String workerId, byte[] spki, Instant moment) {
        OffsetDateTime stamp = moment.atOffset(ZoneOffset.UTC);
        dsl.insertInto(DEVICE_KEYS)
                .set(DEVICE_KEYS.DEVICE_ID, deviceId)
                .set(DEVICE_KEYS.WORKER_ID, workerId)
                .set(DEVICE_KEYS.PUBLIC_KEY, spki)
                .set(DEVICE_KEYS.STATUS, ACTIVE)
                .set(DEVICE_KEYS.UPDATED_AT, stamp)
                .onConflict(DEVICE_KEYS.DEVICE_ID)
                .doUpdate()
                .set(DEVICE_KEYS.WORKER_ID, workerId)
                .set(DEVICE_KEYS.PUBLIC_KEY, spki)
                .set(DEVICE_KEYS.STATUS, ACTIVE)
                .set(DEVICE_KEYS.UPDATED_AT, stamp)
                .where(DEVICE_KEYS.UPDATED_AT.le(stamp))
                .execute();
    }

    /**
     * Отвязывает устройство, если событие новее сохранённого.
     *
     * @param deviceId устройство
     * @param moment       момент события
     */
    public void revoke(String deviceId, Instant moment) {
        OffsetDateTime stamp = moment.atOffset(ZoneOffset.UTC);
        dsl.update(DEVICE_KEYS)
                .set(DEVICE_KEYS.STATUS, REVOKED)
                .set(DEVICE_KEYS.UPDATED_AT, stamp)
                .where(DEVICE_KEYS.DEVICE_ID.eq(deviceId).and(DEVICE_KEYS.UPDATED_AT.le(stamp)))
                .execute();
    }
}
