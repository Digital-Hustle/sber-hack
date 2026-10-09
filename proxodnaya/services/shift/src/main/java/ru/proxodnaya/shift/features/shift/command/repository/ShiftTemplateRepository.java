package ru.proxodnaya.shift.features.shift.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.shift.model.ShiftTemplate;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * Запись шаблонов смен.
 */
@Repository
public class ShiftTemplateRepository {

    private static final ru.proxodnaya.shift.jooq.tables.ShiftTemplates TEMPLATES = Tables.SHIFT_TEMPLATES;

    private final DSLContext dsl;

    public ShiftTemplateRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сохраняет шаблон, если имя свободно.
     *
     * @param template шаблон
     * @param moment   когда
     * @return {@code false}, если имя занято
     */
    public boolean insert(ShiftTemplate template, Instant moment) {
        return dsl.insertInto(TEMPLATES)
                .set(TEMPLATES.ID, template.id())
                .set(TEMPLATES.NAME, template.name())
                .set(TEMPLATES.START_TIME, template.start())
                .set(TEMPLATES.END_TIME, template.end())
                .set(TEMPLATES.CREATED_AT, moment.atOffset(ZoneOffset.UTC))
                .onConflict(TEMPLATES.NAME).doNothing()
                .execute() == 1;
    }

    /**
     * Есть ли шаблон.
     *
     * @param id шаблон
     * @return {@code true}, если есть
     */
    public boolean exists(UUID id) {
        return dsl.fetchExists(TEMPLATES, TEMPLATES.ID.eq(id));
    }
}
