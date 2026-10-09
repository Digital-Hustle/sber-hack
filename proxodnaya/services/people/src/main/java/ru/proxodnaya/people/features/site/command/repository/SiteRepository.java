package ru.proxodnaya.people.features.site.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.site.model.Checkpoint;
import ru.proxodnaya.people.features.site.model.Site;
import ru.proxodnaya.people.features.site.model.Zone;
import ru.proxodnaya.people.jooq.Tables;

/**
 * Объекты, зоны, проходные (запись). Уникальность имён держит БД.
 */
@Repository
public class SiteRepository {

    private final DSLContext dsl;

    public SiteRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Вставляет объект.
     *
     * @param site   объект
     * @param moment момент
     */
    public void insert(Site site, Instant moment) {
        dsl.insertInto(Tables.SITES)
                .set(Tables.SITES.ID, site.id())
                .set(Tables.SITES.NAME, site.name())
                .set(Tables.SITES.ADDRESS, site.address())
                .set(Tables.SITES.CREATED_AT, moment.atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Вставляет зону.
     *
     * @param zone   зона
     * @param moment момент
     */
    public void insert(Zone zone, Instant moment) {
        dsl.insertInto(Tables.ZONES)
                .set(Tables.ZONES.ID, zone.id())
                .set(Tables.ZONES.SITE_ID, zone.siteId())
                .set(Tables.ZONES.NAME, zone.name())
                .set(Tables.ZONES.CAPACITY, zone.capacity())
                .set(Tables.ZONES.CREATED_AT, moment.atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Вставляет проходную.
     *
     * @param checkpoint проходная
     * @param moment     момент
     */
    public void insert(Checkpoint checkpoint, Instant moment) {
        dsl.insertInto(Tables.CHECKPOINTS)
                .set(Tables.CHECKPOINTS.ID, checkpoint.id())
                .set(Tables.CHECKPOINTS.ZONE_ID, checkpoint.zoneId())
                .set(Tables.CHECKPOINTS.NAME, checkpoint.name())
                .set(Tables.CHECKPOINTS.CREATED_AT, moment.atOffset(ZoneOffset.UTC))
                .execute();
    }
}
