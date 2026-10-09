package ru.documents.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ru.documents.intake.features.applications.command.service.DocumentEventsService;

/** Сквозной сценарий соискателя + IDOR/ролевая матрица (FR-01, FR-10, FR-14, NFR-04). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntakeFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/applicant/applications";
    private static final String AUTH = "Authorization";
    private static final String IDEM = "Idempotency-Key";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DocumentEventsService events;

    private JsonNode create() throws Exception {
        String body = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentPd\":true,\"contact\":{\"phone\":\"+79990000000\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private static String auth(JsonNode app) {
        return "Applicant " + app.get("accessKey").asText();
    }

    private JsonNode upload(JsonNode app, byte[] bytes, String name) throws Exception {
        String id = app.get("applicationId").asText();
        String body = mvc.perform(multipart(BASE + "/" + id + "/documents")
                        .file(new MockMultipartFile("file", name, "image/png", bytes))
                        .header(AUTH, auth(app))
                        .header(IDEM, UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private static SimpleGrantedAuthority role(String r) {
        return new SimpleGrantedAuthority("ROLE_" + r);
    }

    @Test
    void consentIsRequired() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"consentPd\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void applicantFlowWithChecklistUploadAndSubmit() throws Exception {
        JsonNode app = create();
        String id = app.get("applicationId").asText();
        assertThat(app.get("resumeCode").asText()).isNotBlank();

        mvc.perform(put(BASE + "/" + id + "/profile").header(AUTH, auth(app))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"citizenship\":\"RF\",\"medicalRequired\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredDocuments[0].type").value("PASSPORT_RF"));

        byte[] img = TestFiles.image("png", 900, 700, "ПАСПОРТ");
        JsonNode up = upload(app, img, "passport.png");
        assertThat(up.get("documentId").asText()).isNotBlank();
        assertThat(up.get("stage").asText()).isEqualTo("RECEIVED");

        mvc.perform(multipart(BASE + "/" + id + "/documents")
                        .file(new MockMultipartFile("file", "again.png", "image/png", img))
                        .header(AUTH, auth(app))
                        .header(IDEM, UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FILE"))
                .andExpect(jsonPath("$.duplicateOf").value(up.get("documentId").asText()));

        Integer outbox = jdbc.sql("select count(*) from outbox where msg_key = :k")
                .param("k", id).query(Integer.class).single();
        assertThat(outbox).isPositive();

        String view = mvc.perform(get(BASE + "/" + id).header(AUTH, auth(app)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(view).get("missing").toString()).contains("SNILS").contains("INN");

        mvc.perform(post(BASE + "/" + id + "/submit").header(AUTH, auth(app)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missing").isArray());

        mvc.perform(get("/api/v1/files/" + up.get("fileId").asText() + "/content").header(AUTH, auth(app)))
                .andExpect(status().isOk());
    }

    @Test
    void anotherApplicantCannotSeeForeignApplicationOrFile() throws Exception {
        JsonNode a = create();
        JsonNode b = create();
        JsonNode up = upload(a, TestFiles.image("png", 900, 700, "СНИЛС"), "s.png");
        String aid = a.get("applicationId").asText();

        mvc.perform(get(BASE + "/" + aid).header(AUTH, auth(b))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/files/" + up.get("fileId").asText() + "/content").header(AUTH, auth(b)))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/" + aid)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + aid).header(AUTH, "Applicant wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleMatrix() throws Exception {
        JsonNode a = create();
        String aid = a.get("applicationId").asText();
        mvc.perform(get("/api/v1/operator/applications/" + aid).header(AUTH, auth(a)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operator/applications/" + aid).with(jwt().authorities(role("OPERATOR"))))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/intake/applications/" + aid + "/summary")
                        .with(jwt().authorities(role("OPERATOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/intake/applications/" + aid + "/summary")
                        .with(jwt().authorities(role("SERVICE_REVIEW"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/ping").with(jwt().authorities(role("AUDITOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadRejectsUnsupportedTypeWithCode() throws Exception {
        JsonNode a = create();
        String id = a.get("applicationId").asText();
        mvc.perform(multipart(BASE + "/" + id + "/documents")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg",
                                "просто текст".getBytes(StandardCharsets.UTF_8)))
                        .header(AUTH, auth(a))
                        .header(IDEM, UUID.randomUUID().toString()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_UNSUPPORTED"));
    }

    @Test
    void resumeByContactAndCode() throws Exception {
        JsonNode a = create();
        mvc.perform(post("/api/v1/applicant/resume").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneOrEmail\":\"+79990000000\",\"resumeCode\":\""
                                + a.get("resumeCode").asText() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(a.get("applicationId").asText()));
        mvc.perform(post("/api/v1/applicant/resume").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneOrEmail\":\"+79990000000\",\"resumeCode\":\"WRONG-0000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESUME_INVALID"));
    }

    @Test
    void multiDocumentFileIsSplitIntoSeparateDocuments() throws Exception {
        JsonNode app = create();
        String id = app.get("applicationId").asText();
        JsonNode up = upload(app, TestFiles.image("png", 900, 700, "СНИЛС ИНН"), "two.png");
        String source = up.get("documentId").asText();
        JsonNode p = om.readTree("{\"documentId\":\"" + source + "\",\"applicationId\":\"" + id
                + "\",\"split\":[{\"type\":\"INN\",\"pages\":[2]},{\"type\":\"UNKNOWN\",\"pages\":[3]}]}");
        String eventId = UUID.randomUUID().toString();
        assertThat(events.split(eventId, p)).isPresent();
        assertThat(events.split(eventId, p)).isEmpty();                        // тот же eventId
        assertThat(events.split(UUID.randomUUID().toString(), p)).isEmpty();  // INN из этого файла уже выделен

        JsonNode view = om.readTree(mvc.perform(get(BASE + "/" + id).header(AUTH, auth(app)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(view.get("documents")).hasSize(2);
        JsonNode child = view.get("documents").get(1);
        assertThat(child.get("slotType").asText()).isEqualTo("INN");
        assertThat(child.get("sourceDocumentId").asText()).isEqualTo(source);
        assertThat(child.get("pages").toString()).isEqualTo("[2]");
        assertThat(child.get("files").get(0).get("id").asText()).isEqualTo(up.get("fileId").asText());
        Integer received = jdbc.sql("""
                select count(*) from outbox where payload->>'type' = 'DocumentReceived'
                   and payload->'payload'->>'sourceDocumentId' = :s""").param("s", source).query(Integer.class).single();
        assertThat(received).isEqualTo(1);

        String childId = child.get("id").asText();
        mvc.perform(get("/internal/intake/documents/" + childId + "/files")
                        .with(jwt().authorities(role("SERVICE_DOCPROC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].fileId").value(up.get("fileId").asText()))
                .andExpect(jsonPath("$.onlyPages[0]").value(2));
        // выделенный документ не делится повторно
        JsonNode again = om.readTree("{\"documentId\":\"" + childId + "\",\"applicationId\":\"" + id
                + "\",\"split\":[{\"type\":\"SNILS\",\"pages\":[2]}]}");
        assertThat(events.split(UUID.randomUUID().toString(), again)).isEmpty();

        mvc.perform(delete(BASE + "/" + id + "/documents/" + source).header(AUTH, auth(app)))
                .andExpect(status().is2xxSuccessful());
        JsonNode after = om.readTree(mvc.perform(get(BASE + "/" + id).header(AUTH, auth(app)))
                .andReturn().getResponse().getContentAsString());
        assertThat(after.get("documents")).isEmpty();
    }
}
