package ru.documents.docproc.features.processing.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ru.documents.common.security.ServiceTokenProvider;
import ru.documents.docproc.features.processing.model.FileRef;

/**
 * Файлы документа из intake по service account (docs/API.md «Внутренние API»).
 */
@Component
public class IntakeGateway {

    private static final String CODE = "INTAKE_UNAVAILABLE";

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public IntakeGateway(@Qualifier("intakeRestClient") RestClient http, ServiceTokenProvider tokens) {
        this.http = http;
        this.tokens = tokens;
    }

    /**
     * Активные файлы документа по порядку версий.
     *
     * @param documentId документ
     * @return файлы
     */
    public List<FileRef> activeFiles(UUID documentId) {
        try {
            JsonNode rs = http.get().uri("/internal/intake/documents/{id}/files", documentId)
                    .header("Authorization", "Bearer " + tokens.token()).retrieve().body(JsonNode.class);
            List<FileRef> out = new ArrayList<>();
            if (rs != null) {
                for (JsonNode it : rs.path("items")) {
                    out.add(new FileRef(UUID.fromString(it.path("fileId").asText()), it.path("mime").asText(),
                            it.path("pages").asInt(1)));
                }
            }
            return out;
        } catch (RestClientException e) {
            throw new DependencyException(CODE, e);
        }
    }

    /**
     * Содержимое файла (расшифровано intake).
     *
     * @param fileId файл
     * @return байты
     */
    public byte[] content(UUID fileId) {
        try {
            byte[] b = http.get().uri("/internal/intake/files/{id}", fileId)
                    .header("Authorization", "Bearer " + tokens.token()).retrieve().body(byte[].class);
            return b == null ? new byte[0] : b;
        } catch (RestClientException e) {
            throw new DependencyException(CODE, e);
        }
    }
}
