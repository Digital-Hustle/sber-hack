package ru.documents.intake.features.applications.query.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ru.documents.intake.features.applications.model.ApplicationView.OperatorRequest;
import ru.documents.intake.features.applications.model.DocumentView;
import ru.documents.intake.features.applications.model.DocumentView.FileView;
import ru.documents.intake.features.applications.rules.RequirementsEngine.Rule;

/**
 * Чтение заявок и документов.
 */
@Repository
public class ApplicationReadRepository {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public ApplicationReadRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Строка заявки.
     *
     * @param id              заявка
     * @param status          статус
     * @param source          источник
     * @param profile         анкета
     * @param decisionComment комментарий
     * @param phone           телефон
     * @param email           почта
     * @param createdAt       создана
     * @param updatedAt       обновлена
     */
    public record Row(UUID id, String status, String source, Map<String, Object> profile, String decisionComment,
                      String phone, String email, java.time.Instant createdAt, java.time.Instant updatedAt) {
    }

    /** Заявка. */
    public Optional<Row> application(UUID id) {
        return jdbc.sql("select * from applications where id = ?").param(id)
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getString("source"), map(rs.getString("profile")), rs.getString("decision_comment"),
                        rs.getString("contact_phone"), rs.getString("contact_email"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    /** Документы заявки с файлами; выделенный из файла документ (FR-07) показывает файлы исходного. */
    public List<DocumentView> documents(UUID appId) {
        List<DocumentView> docs = jdbc.sql("""
                select * from documents where application_id = ? and not deleted order by created_at""").param(appId)
                .query((rs, n) -> new DocumentView(rs.getObject("id", UUID.class), rs.getString("slot_type"),
                        rs.getString("detected_type"), rs.getString("stage"), rs.getString("reason_code"),
                        map(rs.getString("reason_params")), List.of(), rs.getTimestamp("updated_at").toInstant(),
                        rs.getObject("source_document_id", UUID.class), ints(rs.getArray("only_pages"))))
                .list();
        Map<UUID, List<FileView>> files = new java.util.HashMap<>();
        jdbc.sql("""
                select id, document_id, version, active, mime, pages, size_bytes, uploaded_at from document_files
                 where application_id = ? order by version""").param(appId)
                .query((rs, n) -> {
                    files.computeIfAbsent(rs.getObject("document_id", UUID.class), k -> new java.util.ArrayList<>())
                            .add(new FileView(rs.getObject("id", UUID.class), rs.getInt("version"),
                                    rs.getBoolean("active"), rs.getString("mime"), rs.getInt("pages"),
                                    rs.getLong("size_bytes"), rs.getTimestamp("uploaded_at").toInstant()));
                    return 0;
                }).list();
        return docs.stream().map(d -> {
            List<FileView> own = files.getOrDefault(d.id(), List.of());
            boolean borrowed = own.isEmpty() && d.sourceDocumentId() != null;
            List<FileView> shown = borrowed
                    ? files.getOrDefault(d.sourceDocumentId(), List.of()).stream().filter(FileView::active).toList()
                    : own;
            return new DocumentView(d.id(), d.slotType(), d.detectedType(), d.stage(), d.reasonCode(),
                    d.reasonParams(), shown, d.updatedAt(), d.sourceDocumentId(), borrowed ? d.pages() : null);
        }).toList();
    }

    private static List<Integer> ints(java.sql.Array a) throws java.sql.SQLException {
        if (a == null) {
            return null;
        }
        List<Integer> out = new java.util.ArrayList<>();
        for (Object o : (Object[]) a.getArray()) {
            out.add(((Number) o).intValue());
        }
        return out;
    }

    /** Запросы оператора. */
    public List<OperatorRequest> requests(UUID appId) {
        return jdbc.sql("select * from operator_requests where application_id = ? order by created_at").param(appId)
                .query((rs, n) -> new OperatorRequest(rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class), rs.getString("doc_type"), rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("resolved_at") != null))
                .list();
    }

    /** Правила набора. */
    public List<Rule> rules() {
        return jdbc.sql("select doc_type, title, hint, condition::text, required from doc_requirements order by sort, id")
                .query((rs, n) -> new Rule(rs.getString(1), rs.getString(2), rs.getString(3), tree(rs.getString(4)),
                        rs.getBoolean(5))).list();
    }

    /**
     * Файл для выдачи.
     *
     * @param applicationId заявка
     * @param documentId    документ
     * @param mime          тип
     * @param pages         страниц
     * @param enc           шифртекст
     */
    public record FileRow(UUID applicationId, UUID documentId, String mime, int pages, byte[] enc) {
    }

    /**
     * Назначенный оператор заявки (B-07).
     *
     * @param appId заявка
     * @return логин или пусто
     */
    public Optional<String> assignee(UUID appId) {
        return jdbc.sql("select assignee from applications where id = ?").param(appId).query(String.class).optional();
    }

    /** Файл. */
    public Optional<FileRow> file(UUID fileId) {
        return jdbc.sql("""
                select f.application_id, f.document_id, f.mime, f.pages, f.content_enc from document_files f
                  join documents d on d.id = f.document_id where f.id = ? and not d.deleted""").param(fileId)
                .query((rs, n) -> new FileRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getInt(4), rs.getBytes(5))).optional();
    }

    /**
     * Активный файл документа.
     *
     * @param fileId файл
     * @param mime   тип
     * @param pages  страниц
     */
    public record ActiveFile(UUID fileId, String mime, int pages) {
    }

    /**
     * Файлы для разбора.
     *
     * @param files     активные файлы по порядку
     * @param onlyPages только эти страницы (выделенный документ, FR-07), иначе null
     */
    public record DocumentFiles(List<ActiveFile> files, List<Integer> onlyPages) {
    }

    /**
     * Активные файлы документа по порядку (FR-07: у выделенного документа — файлы исходного и его страницы).
     * Если соискатель дослал файл в выделенный документ, разбирается уже этот файл.
     */
    public DocumentFiles activeFiles(UUID docId) {
        List<ActiveFile> own = files(docId);
        if (!own.isEmpty()) {
            return new DocumentFiles(own, null);
        }
        record Src(UUID source, List<Integer> pages) {
        }
        Optional<Src> src = jdbc.sql("""
                select source_document_id, only_pages from documents where id = ? and source_document_id is not null""")
                .param(docId).query((rs, n) -> new Src(rs.getObject(1, UUID.class), ints(rs.getArray(2)))).optional();
        return src.map(s -> new DocumentFiles(files(s.source()), s.pages())).orElse(new DocumentFiles(own, null));
    }

    private List<ActiveFile> files(UUID docId) {
        return jdbc.sql("select id, mime, pages from document_files where document_id = ? and active order by version")
                .param(docId).query((rs, n) -> new ActiveFile(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getInt(3))).list();
    }

    private Map<String, Object> map(String s) {
        try {
            return s == null ? Map.of() : json.readValue(s, MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode tree(String s) {
        try {
            return json.readTree(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
