package ru.documents.docproc.features.processing.query.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ru.documents.docproc.features.processing.model.DocType;

/**
 * Реестр видов документов (doc_types).
 */
@Repository
public class DocTypeRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public DocTypeRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Все виды по порядку. */
    public List<DocType> all() {
        return jdbc.sql("select code, name, signs, schema::text as s, version from doc_types order by sort")
                .query((rs, n) -> {
                    try {
                        return new DocType(rs.getString("code"), rs.getString("name"), rs.getString("signs"),
                                json.readTree(rs.getString("s")), rs.getInt("version"));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).list();
    }
}
