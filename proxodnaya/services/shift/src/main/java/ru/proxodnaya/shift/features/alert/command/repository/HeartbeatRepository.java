package ru.proxodnaya.shift.features.alert.command.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.jooq.Tables;

/**
 * Последний heartbeat телефона.
 */
@Repository
public class HeartbeatRepository {

    private static final ru.proxodnaya.shift.jooq.tables.Heartbeats HEARTBEATS = Tables.HEARTBEATS;

    private final DSLContext dsl;

    public HeartbeatRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сохраняет как последний.
     *
     * @param workerId    рабочий
     * @param lat         широта
     * @param lon         долгота
     * @param accuracy    точность, м, или {@code null}
     * @param distance    до центра площадки или {@code null}
     * @param insideFence в геозоне или {@code null}
     * @param moment      когда
     */
    public void save(String workerId, double lat, double lon, Double accuracy, Double distance, Boolean insideFence, Instant moment) {
        OffsetDateTime stamp = moment.atOffset(ZoneOffset.UTC);
        dsl.insertInto(HEARTBEATS)
                .set(HEARTBEATS.WORKER_ID, workerId)
                .set(HEARTBEATS.LAT, lat)
                .set(HEARTBEATS.LON, lon)
                .set(HEARTBEATS.ACCURACY_M, accuracy)
                .set(HEARTBEATS.DISTANCE_M, distance)
                .set(HEARTBEATS.INSIDE_FENCE, insideFence)
                .set(HEARTBEATS.AT, stamp)
                .onConflict(HEARTBEATS.WORKER_ID)
                .doUpdate()
                .set(HEARTBEATS.LAT, lat)
                .set(HEARTBEATS.LON, lon)
                .set(HEARTBEATS.ACCURACY_M, accuracy)
                .set(HEARTBEATS.DISTANCE_M, distance)
                .set(HEARTBEATS.INSIDE_FENCE, insideFence)
                .set(HEARTBEATS.AT, stamp)
                .execute();
    }
}
