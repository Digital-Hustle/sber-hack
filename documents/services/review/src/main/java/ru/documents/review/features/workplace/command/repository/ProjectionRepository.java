package ru.documents.review.features.workplace.command.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.documents.review.features.workplace.model.AppRow;
import ru.documents.review.features.workplace.model.DocRow;
import ru.documents.review.features.workplace.model.FieldOverride;
import ru.documents.review.features.workplace.persistence.RowMappers;

/**
 * Запись проекции заявок и документов и действий оператора.
 */
@Repository
public class ProjectionRepository {

    private final JdbcClient jdbc;
    private final RowMappers rows;

    public ProjectionRepository(JdbcClient jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    /** Заявка есть (события разных топиков приходят в любом порядке). */
    public void ensureApp(UUID id, Instant now) {
        jdbc.sql("insert into applications(id, created_at, updated_at) values (?, ?, ?) on conflict do nothing")
                .params(id, RowMappers.ts(now), RowMappers.ts(now)).update();
    }

    /** ApplicationCreated. */
    public void created(UUID id, String source, Instant createdAt, Instant now) {
        ensureApp(id, createdAt);
        jdbc.sql("update applications set source = ?, created_at = ?, updated_at = ? where id = ?")
                .params(source, RowMappers.ts(createdAt), RowMappers.ts(now), id).update();
    }

    /** ProfileChanged. */
    public void profile(UUID id, List<String> required, Instant now) {
        ensureApp(id, now);
        jdbc.sql("update applications set required_types = ?::jsonb, updated_at = ? where id = ?")
                .params(rows.write(required), RowMappers.ts(now), id).update();
    }

    /** Submitted: снова в очередь (в т.ч. после «дослать»). */
    public void submitted(UUID id, List<String> missing, Instant now) {
        ensureApp(id, now);
        jdbc.sql("""
                update applications set status = case when status in ('ACCEPTED','RETURNED') then status
                    else 'SUBMITTED' end, submitted_at = ?, missing = ?::jsonb, updated_at = ? where id = ?""")
                .params(RowMappers.ts(now), rows.write(missing), RowMappers.ts(now), id).update();
    }

    /** Документ есть. */
    public void ensureDoc(UUID doc, UUID app, Instant now) {
        ensureApp(app, now);
        jdbc.sql("""
                insert into documents(id, application_id, stage, received_at, updated_at) values (?, ?, 'RECEIVED', ?, ?)
                on conflict do nothing""").params(doc, app, RowMappers.ts(now), RowMappers.ts(now)).update();
    }

    /** DocumentReceived. */
    public void received(UUID doc, UUID app, String slotType, Instant now) {
        ensureDoc(doc, app, now);
        jdbc.sql("update documents set slot_type = coalesce(?, slot_type), removed = false, updated_at = ? where id = ?")
                .params(slotType, RowMappers.ts(now), doc).update();
        touch(app, now);
    }

    /** FilesChanged: прежний результат и правки больше не относятся к файлам. */
    public void filesChanged(UUID doc, UUID app, Instant now) {
        ensureDoc(doc, app, now);
        reset(doc, "QUEUED", now);
        touch(app, now);
    }

    /** Сбросить результат, подтверждение и правки (новые файлы, смена вида, переразбор). */
    public void reset(UUID doc, String stage, Instant now) {
        jdbc.sql("""
                update documents set stage = ?, result = null, result_id = null, reasons = null, reason_code = null,
                    expires_on = null, confirmed_by = null, confirmed_at = null, updated_at = ? where id = ?""")
                .params(stage, RowMappers.ts(now), doc).update();
        jdbc.sql("delete from field_overrides where document_id = ?").param(doc).update();
    }

    /** DocumentRemoved. */
    public void removed(UUID doc, Instant now) {
        jdbc.sql("update documents set removed = true, updated_at = ? where id = ?").params(RowMappers.ts(now), doc).update();
    }

    /** StageChanged. */
    public void stage(UUID doc, UUID app, String stage, String type, String reason, Instant now) {
        ensureDoc(doc, app, now);
        jdbc.sql("""
                update documents set stage = ?, type = coalesce(?, type), reason_code = ?, updated_at = ?
                where id = ?""").params(stage, type, reason, RowMappers.ts(now), doc).update();
        touch(app, now);
    }

    /** DocumentParsed / DocumentUnreadable / DocumentFailed. */
    public void finished(UUID doc, UUID app, String stage, String type, Double typeConfidence, String reasonsJson,
                         String resultId, Instant now) {
        ensureDoc(doc, app, now);
        jdbc.sql("""
                update documents set stage = ?, type = coalesce(?, type), type_confidence = ?, reasons = ?::jsonb,
                    result = case when result_id is distinct from ? then null else result end, result_id = ?,
                    parsed_at = ?, updated_at = ? where id = ?""")
                .params(stage, type, typeConfidence, reasonsJson, resultId, resultId, RowMappers.ts(now), RowMappers.ts(now), doc).update();
        touch(app, now);
    }

    /** Кэш результата docproc. */
    public void cacheResult(UUID doc, String resultJson, String resultId, String type) {
        jdbc.sql("update documents set result = ?::jsonb, result_id = coalesce(?, result_id), type = coalesce(?, type) "
                + "where id = ?").params(resultJson, resultId, type, doc).update();
    }

    /** Производные: срок действия документа. */
    public void expires(UUID doc, LocalDate on) {
        jdbc.sql("update documents set expires_on = ? where id = ?").params(on, doc).update();
    }

    /** Производные: ФИО соискателя. */
    public void applicantName(UUID app, String name) {
        jdbc.sql("update applications set applicant_name = ? where id = ?").params(name, app).update();
    }

    /** Заявка под блокировкой. */
    public Optional<AppRow> lockApp(UUID id) {
        return jdbc.sql("select " + RowMappers.APP_COLUMNS + " from applications a where a.id = ? for update")
                .param(id).query(rows::app).optional();
    }

    /** Следующая свободная заявка (двое не возьмут одну). */
    public Optional<UUID> nextFree() {
        return jdbc.sql("""
                select id from applications where status = 'SUBMITTED' and assignee is null
                order by submitted_at, id limit 1 for update skip locked""").query(UUID.class).optional();
    }

    /** Назначить. */
    public void assign(UUID id, String user, Instant now) {
        jdbc.sql("""
                update applications set assignee = ?, assigned_at = ?, updated_at = ?,
                    status = case when status = 'SUBMITTED' then 'IN_REVIEW' else status end where id = ?""")
                .params(user, RowMappers.ts(now), RowMappers.ts(now), id).update();
    }

    /** Статус. */
    public void status(UUID id, String status, Instant now) {
        jdbc.sql("update applications set status = ?, updated_at = ? where id = ?").params(status, RowMappers.ts(now), id)
                .update();
    }

    /** Решение. */
    public void decide(UUID id, UUID decisionId, String decision, String comment, String actor, Instant now) {
        jdbc.sql("insert into decisions(id, application_id, decision, comment, actor, created_at) values (?,?,?,?,?,?)")
                .params(decisionId, id, decision, comment, actor, RowMappers.ts(now)).update();
        jdbc.sql("update applications set status = ?, decided_at = ?, updated_at = ? where id = ?")
                .params(decision, RowMappers.ts(now), RowMappers.ts(now), id).update();
    }

    /** Документ. */
    public Optional<DocRow> doc(UUID id) {
        return jdbc.sql("select " + RowMappers.DOC_COLUMNS + " from documents d where d.id = ?").param(id)
                .query(rows::doc).optional();
    }

    /** Документы заявки (без удалённых). */
    public List<DocRow> docs(UUID app) {
        return jdbc.sql("select " + RowMappers.DOC_COLUMNS + " from documents d where d.application_id = ? "
                + "and not d.removed order by d.received_at, d.id").param(app).query(rows::doc).list();
    }

    /** Подтвердить документ. */
    public void confirm(UUID doc, String user, Instant now) {
        jdbc.sql("update documents set confirmed_by = ?, confirmed_at = ?, updated_at = ? where id = ?")
                .params(user, RowMappers.ts(now), RowMappers.ts(now), doc).update();
    }

    /** Правка поля. */
    public void override(UUID id, UUID doc, String name, String value, String author, Instant now) {
        jdbc.sql("insert into field_overrides(id, document_id, name, value, author, created_at) values (?,?,?,?,?,?)")
                .params(id, doc, name, value, author, RowMappers.ts(now)).update();
    }

    /** Последние правки документа. */
    public List<FieldOverride> overrides(UUID doc) {
        return jdbc.sql("""
                select distinct on (name) name, value, author, created_at from field_overrides
                where document_id = ? order by name, created_at desc""").param(doc).query(rows::override).list();
    }

    /** Обновлено. */
    public void touch(UUID app, Instant now) {
        jdbc.sql("update applications set updated_at = ? where id = ?").params(RowMappers.ts(now), app).update();
    }
}
