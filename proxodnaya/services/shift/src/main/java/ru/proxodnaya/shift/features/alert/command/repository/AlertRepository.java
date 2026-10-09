package ru.proxodnaya.shift.features.alert.command.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.alert.model.AlertCandidate;
import ru.proxodnaya.shift.features.alert.model.AlertType;
import ru.proxodnaya.shift.features.alert.model.OnSiteWorker;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * Оповещения и выборки для правил.
 */
@Repository
public class AlertRepository {

    private static final ru.proxodnaya.shift.jooq.tables.Alerts ALERTS = Tables.ALERTS;
    private static final ru.proxodnaya.shift.jooq.tables.PresenceIntervals INTERVALS = Tables.PRESENCE_INTERVALS;
    private static final ru.proxodnaya.shift.jooq.tables.Shifts SHIFTS = Tables.SHIFTS;
    private static final ru.proxodnaya.shift.jooq.tables.Heartbeats HEARTBEATS = Tables.HEARTBEATS;

    private final DSLContext dsl;

    public AlertRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Кто сейчас на объекте (свежий открытый интервал) и в смене, с последним heartbeat.
     *
     * @param now        текущий момент
     * @param freshSince граница свежести интервала
     * @return люди
     */
    public List<OnSiteWorker> onSiteInShift(Instant now, Instant freshSince) {
        OffsetDateTime moment = utc(now);
        return dsl.select(INTERVALS.WORKER_ID, INTERVALS.ZONE_ID, INTERVALS.ENTERED_AT, HEARTBEATS.AT, HEARTBEATS.INSIDE_FENCE,
                        HEARTBEATS.DISTANCE_M)
                .from(INTERVALS)
                .leftJoin(HEARTBEATS).on(HEARTBEATS.WORKER_ID.eq(INTERVALS.WORKER_ID))
                .where(INTERVALS.EXITED_AT.isNull())
                .and(INTERVALS.SIMULATED.isFalse())
                .and(INTERVALS.ENTERED_AT.ge(utc(freshSince)))
                .andExists(DSL.selectOne().from(SHIFTS).where(SHIFTS.WORKER_ID.eq(INTERVALS.WORKER_ID))
                        .and(SHIFTS.STARTS_AT.le(moment)).and(SHIFTS.ENDS_AT.gt(moment)))
                .orderBy(INTERVALS.WORKER_ID)
                .fetch(row -> new OnSiteWorker(row.value1(), row.value2(), row.value3().toInstant(),
                        row.value4() == null ? null : row.value4().toInstant(), row.value5(), row.value6()));
    }

    /**
     * Сколько человек в каждой зоне (свежие открытые интервалы).
     *
     * @param freshSince граница свежести
     * @return пары «зона — число», зона не {@code null}
     */
    public List<ZoneCount> insideByZone(Instant freshSince) {
        return dsl.select(INTERVALS.ZONE_ID, DSL.count())
                .from(INTERVALS)
                .where(INTERVALS.EXITED_AT.isNull())
                .and(INTERVALS.SIMULATED.isFalse())
                .and(INTERVALS.ZONE_ID.isNotNull())
                .and(INTERVALS.ENTERED_AT.ge(utc(freshSince)))
                .groupBy(INTERVALS.ZONE_ID)
                .fetch(row -> new ZoneCount(row.value1(), row.value2()));
    }

    /**
     * Есть ли активное оповещение с таким ключом.
     *
     * @param key ключ
     * @return да/нет
     */
    public boolean activeExists(String key) {
        return dsl.fetchExists(ALERTS, ALERTS.DEDUP_KEY.eq(key).and(ALERTS.RESOLVED_AT.isNull()));
    }

    /**
     * Записывает новое оповещение.
     *
     * @param id        id
     * @param candidate условие
     * @param moment    когда
     */
    public void insert(UUID id, AlertCandidate candidate, Instant moment) {
        dsl.insertInto(ALERTS)
                .set(ALERTS.ID, id)
                .set(ALERTS.TYPE, candidate.type().name())
                .set(ALERTS.SEVERITY, candidate.type().severity())
                .set(ALERTS.WORKER_ID, candidate.workerId())
                .set(ALERTS.ZONE_ID, candidate.zoneId())
                .set(ALERTS.DETAILS, candidate.details())
                .set(ALERTS.DEDUP_KEY, candidate.key())
                .set(ALERTS.CREATED_AT, utc(moment))
                .execute();
    }

    /**
     * Снимает активные оповещения этих типов, условия которых больше нет.
     *
     * @param types  типы, которые проверило правило
     * @param keep   ключи, условия которых держатся
     * @param moment когда
     * @return сколько снято
     */
    public int resolveExcept(Collection<AlertType> types, Collection<String> keep, Instant moment) {
        Condition where = ALERTS.RESOLVED_AT.isNull().and(ALERTS.TYPE.in(types.stream().map(Enum::name).toList()));
        if (!keep.isEmpty()) {
            where = where.and(ALERTS.DEDUP_KEY.notIn(keep));
        }
        return dsl.update(ALERTS).set(ALERTS.RESOLVED_AT, utc(moment)).where(where).execute();
    }

    /**
     * Принять оповещение; уже принятое не меняется.
     *
     * @param id     id
     * @param userId кто
     * @param moment когда
     * @return есть ли такое оповещение
     */
    public boolean ack(UUID id, String userId, Instant moment) {
        dsl.update(ALERTS).set(ALERTS.ACKED_BY, userId).set(ALERTS.ACKED_AT, utc(moment))
                .where(ALERTS.ID.eq(id)).and(ALERTS.ACKED_AT.isNull()).execute();
        return dsl.fetchExists(ALERTS, ALERTS.ID.eq(id));
    }

    private static OffsetDateTime utc(Instant moment) {
        return moment.atOffset(ZoneOffset.UTC);
    }

    /**
     * Зона и число людей.
     *
     * @param zoneId зона
     * @param inside человек
     */
    public record ZoneCount(String zoneId, long inside) {
    }
}
