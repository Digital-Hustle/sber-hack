package ru.proxodnaya.people.features.device.query.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.device.model.Device;
import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.WorkerDevices;

/**
 * Устройства рабочих (чтение).
 */
@Repository
public class DeviceReadRepository {

    private static final WorkerDevices DEVICES = Tables.WORKER_DEVICES;

    private final DSLContext dsl;

    public DeviceReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Устройства рабочего, новые сверху.
     *
     * @param workerId рабочий
     * @return устройства
     */
    public List<Device> byWorker(UUID workerId) {
        return dsl.selectFrom(DEVICES).where(DEVICES.WORKER_ID.eq(workerId)).orderBy(DEVICES.BOUND_AT.desc())
                .fetch(row -> new Device(row.getDeviceId(), row.getWorkerId(), row.getPublicKeyJwk().data(),
                        row.getStatus(), row.getBoundAt().toInstant(), instant(row.getRevokedAt())));
    }

    private static java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
