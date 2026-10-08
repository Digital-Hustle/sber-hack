package ru.proxodnaya.access.features.qr.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.proxodnaya.access.jooq.Tables;

/**
 * Погашенные QR: PK {@code (device_id, qr_window)}.
 */
@Repository
public class QrUseRepository {

    private static final ru.proxodnaya.access.jooq.tables.QrUses QR_USES = Tables.QR_USES;

    private final DSLContext dsl;

    public QrUseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Атомарно гасит пару устройство + окно.
     *
     * @param deviceId  устройство
     * @param window    окно
     * @param attemptId попытка
     * @param moment        момент
     * @return {@code true}, если строка вставлена (QR не был погашен)
     */
    public boolean insertIfAbsent(String deviceId, long window, UUID attemptId, Instant moment) {
        return dsl.insertInto(QR_USES)
                .set(QR_USES.DEVICE_ID, deviceId)
                .set(QR_USES.QR_WINDOW, window)
                .set(QR_USES.ATTEMPT_ID, attemptId)
                .set(QR_USES.USED_AT, moment.atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute() == 1;
    }
}
