package ru.proxodnaya.people.features.device.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.device.model.Device;
import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.WorkerDevices;

/**
 * Устройства рабочих (запись).
 */
@Repository
public class DeviceRepository {

    private static final WorkerDevices DEVICES = Tables.WORKER_DEVICES;

    private final DSLContext dsl;

    public DeviceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Есть ли устройство с таким id (в любом статусе).
     *
     * @param deviceId устройство
     * @return есть ли
     */
    public boolean exists(String deviceId) {
        return dsl.fetchExists(DEVICES, DEVICES.DEVICE_ID.eq(deviceId));
    }

    /**
     * Отзывает действующие устройства рабочего (или одно, если задан deviceId).
     *
     * @param workerId рабочий
     * @param deviceId устройство или {@code null} — все
     * @param moment   момент
     * @return отозванные id
     */
    public List<String> revoke(UUID workerId, String deviceId, Instant moment) {
        var condition = DEVICES.WORKER_ID.eq(workerId).and(DEVICES.STATUS.eq(Device.ACTIVE));
        if (deviceId != null) {
            condition = condition.and(DEVICES.DEVICE_ID.eq(deviceId));
        }
        return dsl.update(DEVICES)
                .set(DEVICES.STATUS, Device.REVOKED)
                .set(DEVICES.REVOKED_AT, moment.atOffset(ZoneOffset.UTC))
                .where(condition)
                .returning(DEVICES.DEVICE_ID)
                .fetch(DEVICES.DEVICE_ID);
    }

    /**
     * Привязывает устройство.
     *
     * @param deviceId устройство
     * @param workerId рабочий
     * @param jwk      публичный JWK
     * @param moment   момент
     */
    public void bind(String deviceId, UUID workerId, String jwk, Instant moment) {
        dsl.insertInto(DEVICES)
                .set(DEVICES.DEVICE_ID, deviceId)
                .set(DEVICES.WORKER_ID, workerId)
                .set(DEVICES.PUBLIC_KEY_JWK, JSONB.valueOf(jwk))
                .set(DEVICES.STATUS, Device.ACTIVE)
                .set(DEVICES.BOUND_AT, moment.atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Устройство по id (для device-токена).
     *
     * @param deviceId устройство
     * @return устройство или пусто
     */
    public Optional<Device> find(String deviceId) {
        return dsl.selectFrom(DEVICES).where(DEVICES.DEVICE_ID.eq(deviceId))
                .fetchOptional(row -> new Device(row.getDeviceId(), row.getWorkerId(), row.getPublicKeyJwk().data(),
                        row.getStatus(), row.getBoundAt().toInstant(), null));
    }
}
