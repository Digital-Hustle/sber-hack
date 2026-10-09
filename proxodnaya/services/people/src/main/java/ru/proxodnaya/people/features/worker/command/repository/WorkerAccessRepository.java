package ru.proxodnaya.people.features.worker.command.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.worker.model.Permit;
import ru.proxodnaya.people.jooq.Tables;

/**
 * Допуски рабочего: зоны и сроки (запись целиком, заменой).
 */
@Repository
public class WorkerAccessRepository {

    private final DSLContext dsl;

    public WorkerAccessRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Заменяет зоны допуска.
     *
     * @param workerId рабочий
     * @param zoneIds  зоны
     */
    public void replaceZones(UUID workerId, Set<UUID> zoneIds) {
        dsl.deleteFrom(Tables.WORKER_ZONES).where(Tables.WORKER_ZONES.WORKER_ID.eq(workerId)).execute();
        for (UUID zoneId : zoneIds) {
            dsl.insertInto(Tables.WORKER_ZONES)
                    .set(Tables.WORKER_ZONES.WORKER_ID, workerId)
                    .set(Tables.WORKER_ZONES.ZONE_ID, zoneId)
                    .execute();
        }
    }

    /**
     * Заменяет допуски со сроком.
     *
     * @param workerId рабочий
     * @param permits  допуски (вид не повторяется)
     */
    public void replacePermits(UUID workerId, List<Permit> permits) {
        dsl.deleteFrom(Tables.PERMITS).where(Tables.PERMITS.WORKER_ID.eq(workerId)).execute();
        for (Permit permit : permits) {
            dsl.insertInto(Tables.PERMITS)
                    .set(Tables.PERMITS.WORKER_ID, workerId)
                    .set(Tables.PERMITS.TYPE, permit.type().name())
                    .set(Tables.PERMITS.VALID_UNTIL, permit.validUntil())
                    .execute();
        }
    }
}
