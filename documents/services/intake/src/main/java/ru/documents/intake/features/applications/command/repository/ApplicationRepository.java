package ru.documents.intake.features.applications.command.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Запись заявок, документов, файлов и служебных таблиц intake.
 */
@Repository
public class ApplicationRepository {

    private final JdbcClient jdbc;

    public ApplicationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }

    /** Новая заявка. */
    public void insertApplication(UUID id, String keyHash, String codeHash, String phone, String email, String source,
                                  Instant now) {
        jdbc.sql("""
                insert into applications(id, access_key_hash, resume_code_hash, contact_phone, contact_email, source,
                  consent_at, created_at, updated_at) values (?,?,?,?,?,?,?,?,?)""")
                .params(id, keyHash, codeHash, phone, email, source, ts(now), ts(now), ts(now)).update();
    }

    /** Статус заявки. */
    public Optional<String> status(UUID id) {
        return jdbc.sql("select status from applications where id = ? for update").param(id).query(String.class)
                .optional();
    }

    /** Сохранить анкету. */
    public void updateProfile(UUID id, String profileJson, Instant now) {
        jdbc.sql("update applications set profile = ?::jsonb, updated_at = ? where id = ?")
                .params(profileJson, ts(now), id).update();
    }

    /** Сменить статус. */
    public void setStatus(UUID id, String status, String comment, Instant now) {
        jdbc.sql("update applications set status = ?, decision_comment = coalesce(?, decision_comment), updated_at = ?"
                        + " where id = ? and status <> 'DELETED'").params(status, comment, ts(now), id).update();
    }

    /** Назначенный оператор (B-07, из события review ApplicationAssigned). */
    public void setAssignee(UUID id, String assignee, Instant now) {
        jdbc.sql("update applications set assignee = ?, updated_at = ? where id = ? and status <> 'DELETED'")
                .params(assignee, ts(now), id).update();
    }

    /** Отметить изменение заявки. */
    public void touch(UUID id, Instant now) {
        jdbc.sql("update applications set updated_at = ? where id = ?").params(ts(now), id).update();
    }

    /** Активных файлов в заявке. */
    public int activeFiles(UUID appId) {
        return jdbc.sql("""
                select count(*) from document_files f join documents d on d.id = f.document_id
                 where f.application_id = ? and f.active and not d.deleted""").param(appId).query(Integer.class).single();
    }

    /** Загрузок с момента. */
    public int uploadsSince(UUID appId, Instant since) {
        return jdbc.sql("select count(*) from document_files where application_id = ? and uploaded_at > ?")
                .params(appId, ts(since)).query(Integer.class).single();
    }

    /**
     * Точный дубликат в заявке.
     *
     * @param documentId документ
     * @param type       вид или пункт
     */
    public record Duplicate(UUID documentId, String type) {
    }

    /** Найти точный дубликат по sha256. */
    public Optional<Duplicate> duplicate(UUID appId, String sha) {
        return jdbc.sql("""
                select d.id, coalesce(d.detected_type, d.slot_type) as t from document_files f
                  join documents d on d.id = f.document_id
                 where f.application_id = ? and f.sha256 = ? and f.active and not d.deleted limit 1""")
                .params(appId, sha).query((rs, n) -> new Duplicate(rs.getObject(1, UUID.class), rs.getString(2)))
                .optional();
    }

    /**
     * Perceptual hash снимка.
     *
     * @param documentId документ
     * @param phash      хэш
     */
    public record Phash(UUID documentId, long phash) {
    }

    /** Хэши снимков заявки. */
    public List<Phash> phashes(UUID appId) {
        return jdbc.sql("""
                select f.document_id, f.phash from document_files f join documents d on d.id = f.document_id
                 where f.application_id = ? and f.phash is not null and f.active and not d.deleted""")
                .param(appId).query((rs, n) -> new Phash(rs.getObject(1, UUID.class), rs.getLong(2))).list();
    }

    /** Новый документ. */
    public void insertDocument(UUID id, UUID appId, String slot, Instant now) {
        jdbc.sql("""
                insert into documents(id, application_id, slot_type, stage, created_at, updated_at)
                values (?,?,?,'RECEIVED',?,?)""").params(id, appId, slot, ts(now), ts(now)).update();
    }

    /**
     * Документ, выделенный из многостраничного файла (FR-07): пункт = найденный вид, файлы — у исходного.
     *
     * @return true — создан; false — такой вид из этого файла уже выделен
     */
    public boolean insertSplit(UUID id, UUID appId, UUID source, String type, List<Integer> pages, Instant now) {
        return jdbc.sql("""
                insert into documents(id, application_id, slot_type, stage, source_document_id, only_pages, split_type,
                                      created_at, updated_at)
                select ?, ?, ?, 'RECEIVED', ?, ?::int[], ?, ?, ?
                 where exists (select 1 from documents s where s.id = ? and not s.deleted and s.source_document_id is null)
                on conflict do nothing""")
                .params(id, appId, type, source, "{" + String.join(",", pages.stream().map(String::valueOf).toList()) + "}",
                        type, ts(now), ts(now), source).update() == 1;
    }

    /** Живые документы, выделенные из этого. */
    public List<UUID> children(UUID source) {
        return jdbc.sql("select id from documents where source_document_id = ? and not deleted").param(source)
                .query(UUID.class).list();
    }

    /** Новый файл (версия). */
    public void insertFile(UUID id, UUID docId, UUID appId, int version, String sha, Long phash, String mime,
                           long size, int pages, Integer w, Integer h, byte[] enc, Instant now) {
        jdbc.sql("""
                insert into document_files(id, document_id, application_id, version, sha256, phash, mime, size_bytes,
                  pages, width, height, content_enc, uploaded_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?)""")
                .params(id, docId, appId, version, sha, phash, mime, size, pages, w, h, enc, ts(now)).update();
    }

    /** Следующая версия файла документа. */
    public int nextVersion(UUID docId) {
        return jdbc.sql("select coalesce(max(version), 0) + 1 from document_files where document_id = ?")
                .param(docId).query(Integer.class).single();
    }

    /** Снять активность со старых файлов (замена). */
    public void deactivateFiles(UUID docId) {
        jdbc.sql("update document_files set active = false where document_id = ?").param(docId).update();
    }

    /** Заявка документа (если не удалён). */
    public Optional<UUID> documentApplication(UUID docId) {
        return jdbc.sql("select application_id from documents where id = ? and not deleted").param(docId)
                .query(UUID.class).optional();
    }

    /** Вернуть документ на разбор после дозагрузки. */
    public void resetStage(UUID docId, Instant now) {
        jdbc.sql("update documents set stage = 'RECEIVED', reason_code = null, reason_params = '{}'::jsonb,"
                + " updated_at = ? where id = ?").params(ts(now), docId).update();
    }

    /** Удалить документ (мягко, файлы стираются). */
    public void deleteDocument(UUID docId, Instant now) {
        jdbc.sql("update documents set deleted = true, updated_at = ? where id = ?").params(ts(now), docId).update();
        jdbc.sql("update document_files set active = false, content_enc = ''::bytea where document_id = ?")
                .param(docId).update();
    }

    /** Неудалённые документы заявки (вместе с выделенными). */
    public List<UUID> liveDocuments(UUID appId) {
        return jdbc.sql("select id from documents where application_id = ? and not deleted order by created_at, id")
                .param(appId).query(UUID.class).list();
    }

    /**
     * Стереть ПДн заявки (NFR-10): анкета, контакты, комментарий, запросы оператора, содержимое всех версий файлов.
     * Ключ и код заменяются — старая ссылка больше не открывает заявку. Остаётся строка со статусом DELETED.
     */
    public void erase(UUID appId, String deadKeyHash, Instant now) {
        jdbc.sql("""
                update applications set status = 'DELETED', profile = '{}'::jsonb, contact_phone = null,
                    contact_email = null, decision_comment = null, access_key_hash = ?, resume_code_hash = '',
                    updated_at = ? where id = ?""").params(deadKeyHash, ts(now), appId).update();
        jdbc.sql("update documents set reason_params = '{}'::jsonb where application_id = ?").param(appId).update();
        jdbc.sql("update document_files set active = false, content_enc = ''::bytea where application_id = ?")
                .param(appId).update();
        jdbc.sql("delete from operator_requests where application_id = ?").param(appId).update();
        jdbc.sql("delete from upload_requests where application_id = ?").param(appId).update();
        jdbc.sql("delete from stream_tickets where application_id = ?").param(appId).update();
    }

    /** Ответ на запрос с тем же Idempotency-Key. */
    public Optional<String> idempotent(UUID appId, String key) {
        return jdbc.sql("select response::text from upload_requests where application_id = ? and idempotency_key = ?")
                .params(appId, key).query(String.class).optional();
    }

    /** Запомнить ответ для Idempotency-Key. */
    public void saveIdempotent(UUID appId, String key, String json, Instant now) {
        jdbc.sql("""
                insert into upload_requests(idempotency_key, application_id, response, created_at)
                values (?,?,?::jsonb,?) on conflict do nothing""").params(key, appId, json, ts(now)).update();
    }

    /** Заявки по контакту. */
    public List<UUID> byContact(String contact, String codeHash) {
        return jdbc.sql("""
                select id from applications where (contact_phone = ? or lower(contact_email) = lower(?))
                   and resume_code_hash = ?""").params(contact, contact, codeHash).query(UUID.class).list();
    }

    /** Неудачных попыток по контакту в текущем окне. */
    public int resumeFailures(String contactHash, Instant windowStart) {
        return jdbc.sql("select failures from resume_attempts where contact_hash = ? and window_start > ?")
                .params(contactHash, ts(windowStart)).query(Integer.class).optional().orElse(0);
    }

    /** +1 неудачная попытка. */
    public void resumeFailed(String contactHash, Instant now, Instant windowStart) {
        jdbc.sql("""
                insert into resume_attempts(contact_hash, failures, window_start) values (?, 1, ?)
                on conflict (contact_hash) do update set
                  failures = case when resume_attempts.window_start > ? then resume_attempts.failures + 1 else 1 end,
                  window_start = case when resume_attempts.window_start > ? then resume_attempts.window_start else ? end""")
                .params(contactHash, ts(now), ts(windowStart), ts(windowStart), ts(now)).update();
    }

    /** Сбросить попытки. */
    public void resumeReset(String contactHash) {
        jdbc.sql("delete from resume_attempts where contact_hash = ?").param(contactHash).update();
    }

    /** Новый ключ заявки (старый перестаёт работать). */
    public void rotateKey(UUID appId, String keyHash, Instant now) {
        jdbc.sql("update applications set access_key_hash = ?, updated_at = ? where id = ?")
                .params(keyHash, ts(now), appId).update();
    }

    /** Билет SSE. */
    public void insertTicket(String hash, UUID appId, Instant expires) {
        jdbc.sql("insert into stream_tickets(ticket_hash, application_id, expires_at) values (?,?,?)")
                .params(hash, appId, ts(expires)).update();
    }

    /** Погасить билет SSE (одноразовый). */
    public Optional<UUID> consumeTicket(String hash, Instant now) {
        return jdbc.sql("delete from stream_tickets where ticket_hash = ? and expires_at > ? returning application_id")
                .params(hash, ts(now)).query(UUID.class).optional();
    }

    /** Стадия от docproc. */
    public Optional<UUID> updateStage(UUID docId, String stage, String type, String reason, String paramsJson,
                                      Instant now) {
        return jdbc.sql("""
                update documents set stage = ?, detected_type = coalesce(?, detected_type), reason_code = ?,
                  reason_params = coalesce(?::jsonb, '{}'::jsonb), updated_at = ?
                 where id = ? and not deleted returning application_id""")
                .params(stage, type, reason, paramsJson, ts(now), docId).query(UUID.class).optional();
    }

    /** Пункт документа. */
    public Optional<String> slot(UUID docId) {
        return jdbc.sql("select slot_type from documents where id = ?").param(docId).query(String.class).optional();
    }

    /** Перенести в пункт по виду. */
    public void moveSlot(UUID docId, String type) {
        jdbc.sql("update documents set slot_type = ? where id = ?").params(type, docId).update();
    }

    /** Запрос оператора. */
    public void insertRequest(UUID id, UUID appId, UUID docId, String type, String reason, Instant now) {
        jdbc.sql("""
                insert into operator_requests(id, application_id, document_id, doc_type, reason, created_at)
                values (?,?,?,?,?,?) on conflict do nothing""").params(id, appId, docId, type, reason, ts(now)).update();
    }

    /** Закрыть запросы после новой загрузки. */
    public void resolveRequests(UUID appId, UUID docId, String slot, Instant now) {
        jdbc.sql("""
                update operator_requests set resolved_at = ?
                 where application_id = ? and resolved_at is null
                   and (document_id = ? or (document_id is null and doc_type = ?))""")
                .params(ts(now), appId, docId, slot).update();
    }

    /** Аудит просмотра файла. */
    public void logAccess(UUID fileId, String actor, String kind, Instant now) {
        jdbc.sql("insert into file_access_log(file_id, actor, actor_kind, accessed_at) values (?,?,?,?)")
                .params(fileId, actor, kind, ts(now)).update();
    }
}
