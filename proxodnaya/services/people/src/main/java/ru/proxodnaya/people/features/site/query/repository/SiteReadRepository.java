package ru.proxodnaya.people.features.site.query.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.site.model.Checkpoint;
import ru.proxodnaya.people.features.site.model.Site;
import ru.proxodnaya.people.features.site.model.Zone;
import ru.proxodnaya.people.jooq.Tables;

/**
 * Объекты, зоны, проходные (чтение).
 */
@Repository
public class SiteReadRepository {

    private final DSLContext dsl;

    public SiteReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Все объекты по имени.
     *
     * @return объекты
     */
    public List<Site> sites() {
        return dsl.selectFrom(Tables.SITES).orderBy(Tables.SITES.NAME)
                .fetch(row -> new Site(row.getId(), row.getName(), row.getAddress()));
    }

    /**
     * Есть ли объект.
     *
     * @param id объект
     * @return есть ли
     */
    public boolean siteExists(UUID id) {
        return dsl.fetchExists(Tables.SITES, Tables.SITES.ID.eq(id));
    }

    /**
     * Зоны (объекта или все).
     *
     * @param siteId объект или {@code null}
     * @return зоны по имени
     */
    public List<Zone> zones(UUID siteId) {
        Condition condition = siteId == null ? DSL.noCondition() : Tables.ZONES.SITE_ID.eq(siteId);
        return dsl.selectFrom(Tables.ZONES).where(condition).orderBy(Tables.ZONES.NAME)
                .fetch(row -> new Zone(row.getId(), row.getSiteId(), row.getName(), row.getCapacity()));
    }

    /**
     * Зона.
     *
     * @param id зона
     * @return зона
     */
    public Optional<Zone> zone(UUID id) {
        return dsl.selectFrom(Tables.ZONES).where(Tables.ZONES.ID.eq(id))
                .fetchOptional(row -> new Zone(row.getId(), row.getSiteId(), row.getName(), row.getCapacity()));
    }

    /**
     * Какие из зон существуют.
     *
     * @param ids зоны
     * @return существующие
     */
    public Set<UUID> existingZones(Set<UUID> ids) {
        return Set.copyOf(dsl.select(Tables.ZONES.ID).from(Tables.ZONES).where(Tables.ZONES.ID.in(ids)).fetch(Tables.ZONES.ID));
    }

    /**
     * Проходные (зоны или все).
     *
     * @param zoneId зона или {@code null}
     * @return проходные по имени
     */
    public List<Checkpoint> checkpoints(UUID zoneId) {
        Condition condition = zoneId == null ? DSL.noCondition() : Tables.CHECKPOINTS.ZONE_ID.eq(zoneId);
        return dsl.select(Tables.CHECKPOINTS.ID, Tables.CHECKPOINTS.ZONE_ID, Tables.ZONES.SITE_ID, Tables.CHECKPOINTS.NAME)
                .from(Tables.CHECKPOINTS).join(Tables.ZONES).on(Tables.ZONES.ID.eq(Tables.CHECKPOINTS.ZONE_ID))
                .where(condition).orderBy(Tables.CHECKPOINTS.NAME)
                .fetch(row -> new Checkpoint(row.value1(), row.value2(), row.value3(), row.value4()));
    }

    /**
     * Проходная.
     *
     * @param id проходная
     * @return проходная
     */
    public Optional<Checkpoint> checkpoint(UUID id) {
        return dsl.select(Tables.CHECKPOINTS.ID, Tables.CHECKPOINTS.ZONE_ID, Tables.ZONES.SITE_ID, Tables.CHECKPOINTS.NAME)
                .from(Tables.CHECKPOINTS).join(Tables.ZONES).on(Tables.ZONES.ID.eq(Tables.CHECKPOINTS.ZONE_ID))
                .where(Tables.CHECKPOINTS.ID.eq(id))
                .fetchOptional(row -> new Checkpoint(row.value1(), row.value2(), row.value3(), row.value4()));
    }
}
