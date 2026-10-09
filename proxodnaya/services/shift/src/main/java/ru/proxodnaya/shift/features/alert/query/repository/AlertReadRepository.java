package ru.proxodnaya.shift.features.alert.query.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.alert.model.Alert;
import ru.proxodnaya.shift.features.alert.model.AlertType;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * Чтение оповещений.
 */
@Repository
public class AlertReadRepository {

    private static final ru.proxodnaya.shift.jooq.tables.Alerts ALERTS = Tables.ALERTS;

    private final DSLContext dsl;

    public AlertReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Последние оповещения, новые сверху.
     *
     * @param onlyOpen только не принятые
     * @param limit    сколько
     * @return оповещения
     */
    public List<Alert> latest(boolean onlyOpen, int limit) {
        Condition where = onlyOpen ? ALERTS.ACKED_AT.isNull() : DSL.noCondition();
        return dsl.selectFrom(ALERTS).where(where).orderBy(ALERTS.CREATED_AT.desc(), ALERTS.ID.desc()).limit(limit)
                .fetch(AlertReadRepository::alert);
    }

    /**
     * Оповещение по id.
     *
     * @param id id
     * @return оповещение
     */
    public Optional<Alert> byId(UUID id) {
        return dsl.selectFrom(ALERTS).where(ALERTS.ID.eq(id)).fetchOptional(AlertReadRepository::alert);
    }

    private static Alert alert(Record row) {
        return new Alert(row.get(ALERTS.ID), AlertType.valueOf(row.get(ALERTS.TYPE)), row.get(ALERTS.SEVERITY), row.get(ALERTS.WORKER_ID),
                row.get(ALERTS.ZONE_ID), row.get(ALERTS.DETAILS), row.get(ALERTS.CREATED_AT).toInstant(),
                instant(row.get(ALERTS.RESOLVED_AT)), row.get(ALERTS.ACKED_BY), instant(row.get(ALERTS.ACKED_AT)));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
