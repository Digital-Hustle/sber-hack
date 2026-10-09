package ru.proxodnaya.access.features.settings.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.jooq.Tables;
import ru.proxodnaya.access.jooq.tables.AccessSettings;

/**
 * Строка настроек прохода.
 */
@Repository
public class SettingsRepository {

    private static final AccessSettings SETTINGS = Tables.ACCESS_SETTINGS;
    private static final short ROW = 1;

    private final DSLContext dsl;

    public SettingsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сохранённые настройки.
     *
     * @return JSON, момент и автор; пусто — ещё не меняли
     */
    public Optional<Stored> find() {
        return dsl.selectFrom(SETTINGS).where(SETTINGS.ID.eq(ROW))
                .fetchOptional(row -> new Stored(row.getData().data(), row.getUpdatedAt().toInstant(), row.getUpdatedBy()));
    }

    /**
     * Сохраняет настройки целиком.
     *
     * @param json      значения
     * @param moment    момент
     * @param updatedBy кем
     */
    public void save(String json, Instant moment, String updatedBy) {
        dsl.insertInto(SETTINGS)
                .set(SETTINGS.ID, ROW)
                .set(SETTINGS.DATA, JSONB.valueOf(json))
                .set(SETTINGS.UPDATED_AT, moment.atOffset(ZoneOffset.UTC))
                .set(SETTINGS.UPDATED_BY, updatedBy)
                .onConflict(SETTINGS.ID).doUpdate()
                .set(SETTINGS.DATA, JSONB.valueOf(json))
                .set(SETTINGS.UPDATED_AT, moment.atOffset(ZoneOffset.UTC))
                .set(SETTINGS.UPDATED_BY, updatedBy)
                .execute();
    }

    /**
     * Сохранённая строка.
     *
     * @param json      значения
     * @param updatedAt момент
     * @param updatedBy автор
     */
    public record Stored(String json, Instant updatedAt, String updatedBy) {
    }
}
