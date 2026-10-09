package ru.documents.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.documents.review.features.workplace.application.service.WorkplaceService;
import ru.documents.review.features.workplace.gateway.DocprocGateway;
import ru.documents.review.features.workplace.gateway.IntakeGateway;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String APP = "applicationId";
    private static final String DOC = "documentId";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private WorkplaceService workplace;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private IntakeGateway intake;
    @MockitoBean
    private DocprocGateway docproc;

    private UUID app;
    private UUID passport;
    private UUID patent;

    @BeforeEach
    void setUp() {
        app = UUID.randomUUID();
        passport = UUID.randomUUID();
        patent = UUID.randomUUID();
        when(intake.summary(any())).thenAnswer(inv -> Optional.of(node(Map.of("id", app.toString(),
                "profile", Map.of("citizenship", "UZ"), "requests", List.of(),
                "documents", List.of(Map.of("id", passport.toString(), "files", List.of(Map.of("id",
                        UUID.randomUUID().toString(), "version", 1, "active", true, "mime", "image/png",
                        "pages", 1))))))));
        String exp = LocalDate.now().plusDays(10).toString();
        when(docproc.result(passport)).thenReturn(Optional.of(node(Map.of("documentId", passport.toString(),
                "stage", "NEEDS_REVIEW", "type", "PASSPORT_FOREIGN", "typeName", "Паспорт иностранного гражданина",
                "pages", List.of(Map.of("n", 1, "width", 1000, "height", 700)),
                "fields", List.of(field("surname", "Фамилия", "IVANOV", 0.95),
                        field("givenNames", "Имя", "IVAN", 0.5), field("birthDate", "Дата рождения", "1990-01-02", 0.9),
                        field("documentNumber", "Номер паспорта", "AB1234567", 0.92),
                        field("expiryDate", "Срок действия", exp, 0.9))))));
        when(docproc.result(patent)).thenReturn(Optional.of(node(Map.of("documentId", patent.toString(),
                "stage", "PARSED", "type", "WORK_PATENT", "typeName", "Патент на работу",
                "fields", List.of(field("fullName", "ФИО", "Иванов Иван", 0.93),
                        field("birthDate", "Дата рождения", "1990-01-03", 0.9),
                        field("passportNumber", "Паспорт", "AB 1234567", 0.9))))));
    }

    @Test
    void operatorFlowFromEventsToDecision() throws Exception {
        submitWithTwoParsedDocuments();

        mvc.perform(get("/api/v1/applications").param("attention", "true").with(op("op1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.id=='" + app + "')].attention")
                        .value(1));
        mvc.perform(post("/api/v1/applications/" + app + "/assign").with(op("op1")))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/applications/" + app + "/assign").with(op("op2")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_ASSIGNED"));

        String card = mvc.perform(get("/api/v1/applications/" + app).with(op("op1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.application.applicantName").value("IVANOV IVAN"))
                .andExpect(jsonPath("$.documents[0].id").value(passport.toString()))
                .andExpect(jsonPath("$.documents[0].fields[0].name").value("givenNames"))
                .andExpect(jsonPath("$.documents[0].files[0].mime").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        JsonNode c = json.readTree(card);
        assertThat(c.toString()).doesNotContain("AB1234567").contains("•••••4567");
        assertThat(c.path("findings").findValuesAsText("code")).contains("CROSS_MISMATCH", "MISSING_DOCUMENT");
        assertThat(c.path("findings").findValuesAsText("field")).contains("birthDate").doesNotContain("fullName");

        mvc.perform(patch("/api/v1/documents/" + passport + "/fields").with(op("op2")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fields\":[{\"name\":\"givenNames\",\"value\":\"IVAN\"}]}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/documents/" + passport + "/fields").with(op("op1")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fields\":[{\"name\":\"givenNames\",\"value\":\"IVAN AKMAL\"}]}"))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/v1/documents/" + passport + "/fields").with(op("op1")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fields\":[{\"name\":\"nope\",\"value\":\"x\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FIELD_UNKNOWN"));
        mvc.perform(get("/api/v1/applications/" + app).with(op("op1")))
                .andExpect(jsonPath("$.application.applicantName").value("IVANOV IVAN AKMAL"))
                .andExpect(jsonPath("$.documents[?(@.id=='" + passport + "')].fields[?(@.name=='givenNames')].corrected")
                        .value(true))
                .andExpect(jsonPath("$.counters.fieldsCorrected").value(1));

        mvc.perform(post("/api/v1/applications/" + app + "/reveal").with(op("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + passport + "\",\"field\":\"documentNumber\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("AB1234567"));
        mvc.perform(post("/api/v1/documents/" + passport + "/confirm").with(op("op1")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/reports/expiring").param("days", "30").with(op("op1")))
                .andExpect(jsonPath("$.items[?(@.documentId=='" + passport + "')].daysLeft").value(10));

        mvc.perform(post("/api/v1/applications/" + app + "/decision").with(op("op1"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"RETURNED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        mvc.perform(get("/api/v1/applications/" + app + "/export").param("format", "docx").with(senior()))
                .andExpect(status().isOk()).andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        byte[] pdf = mvc.perform(get("/api/v1/applications/" + app + "/export").param("format", "pdf").with(senior()))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var parsed = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(parsed)).contains("Карточка соискателя");
        }
        mvc.perform(get("/api/v1/applications/" + app + "/export").param("format", "odt").with(senior()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FORMAT_UNSUPPORTED"));
        mvc.perform(post("/api/v1/applications/" + app + "/decision").with(op("op1"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/v1/documents/" + passport + "/fields").with(op("op1")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fields\":[{\"name\":\"givenNames\",\"value\":\"I\"}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_DECIDED"));

        List<String> events = jdbc.sql("select payload ->> 'type' from outbox where msg_key = ? order by created_at")
                .param(app.toString()).query(String.class).list();
        assertThat(events).containsSubsequence("ApplicationAssigned", "FieldCorrected", "DocumentConfirmed",
                "ApplicationDecided");
        List<String> actions = jdbc.sql("select action from application_history where application_id = ? "
                + "order by created_at, id").param(app).query(String.class).list();
        assertThat(actions).contains("SUBMITTED", "DOCUMENT_PARSED", "ASSIGNED", "FIELD_CORRECTED", "REVEALED",
                "DOCUMENT_CONFIRMED", "EXPORTED", "DECIDED");
    }

    @Test
    void duplicateEventIsAppliedOnce() {
        String id = UUID.randomUUID().toString();
        workplace.onEvent(id, "Submitted", node(Map.of(APP, app.toString(), "missing", List.of())));
        workplace.onEvent(id, "Submitted", node(Map.of(APP, app.toString(), "missing", List.of())));
        assertThat(jdbc.sql("select count(*) from application_history where application_id = ?").param(app)
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void historyIsAppendOnly() {
        workplace.onEvent(UUID.randomUUID().toString(), "Submitted", node(Map.of(APP, app.toString(),
                "missing", List.of())));
        assertThatThrownBy(() -> jdbc.sql("update application_history set actor = 'x' where application_id = ?")
                .param(app).update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from application_history where application_id = ?")
                .param(app).update()).hasMessageContaining("append-only");
    }

    @Test
    void nextTakesOldestFreeApplication() throws Exception {
        submitWithTwoParsedDocuments();
        String body = mvc.perform(post("/api/v1/applications/next").with(op("op9")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID taken = UUID.fromString(json.readTree(body).path("applicationId").asText());
        assertThat(jdbc.sql("select assignee from applications where id = ?").param(taken).query(String.class)
                .single()).isEqualTo("op9");
    }

    @Test
    void roleMatrix() throws Exception {
        submitWithTwoParsedDocuments();
        RequestPostProcessor auditor = jwt().jwt(j -> j.claim("preferred_username", "aud"))
                .authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"));
        mvc.perform(get("/api/v1/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/applications").param("q", app.toString().substring(0, 8)).with(auditor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].applicantName").value("I••• I•••"));
        mvc.perform(get("/api/v1/applications/" + app).with(auditor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/applications/" + app + "/history").with(auditor)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/metrics").with(auditor)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/applications/" + app + "/assign").with(auditor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/applications/" + app + "/export").with(op("op1"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/expiring/export").with(op("op1"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/documents/" + passport + "/reprocess").with(op("op1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/expiring/export").with(senior())).andExpect(status().isOk());
        mvc.perform(get("/internal/anything").with(op("op1"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/stream").param("ticket", "bad")).andExpect(status().isUnauthorized());
        String t = mvc.perform(get("/api/v1/stream/ticket").with(op("op1"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(t).path("ticket").asText()).hasSizeGreaterThan(30);
    }

    @Test
    void sameNumberInAnotherApplicationIsFlagged() throws Exception {
        submitWithTwoParsedDocuments();
        setUp();
        submitWithTwoParsedDocuments();

        String card = mvc.perform(get("/api/v1/applications/" + app).with(op("op1"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode c = json.readTree(card);
        List<JsonNode> hits = new java.util.ArrayList<>();
        c.path("findings").forEach(f -> {
            if ("NUMBER_ELSEWHERE".equals(f.path("code").asText())) {
                hits.add(f);
            }
        });
        assertThat(hits).extracting(f -> f.path("field").asText()).contains("documentNumber", "passportNumber");
        assertThat(hits).anySatisfy(f -> assertThat(f.path("documentIds").get(0).asText()).isEqualTo(passport.toString()));
        assertThat(hits).allSatisfy(f -> assertThat(f.path("params").path("count").asInt()).isPositive());
        assertThat(c.toString()).doesNotContain("AB1234567");
    }

    private void submitWithTwoParsedDocuments() {
        event("ApplicationCreated", Map.of(APP, app.toString(), "source", "WEB"));
        event("ProfileChanged", Map.of(APP, app.toString(), "requiredTypes",
                List.of("PASSPORT_FOREIGN", "WORK_PATENT", "MEDICAL")));
        event("DocumentReceived", Map.of(APP, app.toString(), DOC, passport.toString(), "slotType",
                "PASSPORT_FOREIGN"));
        event("DocumentReceived", Map.of(APP, app.toString(), DOC, patent.toString()));
        event("Submitted", Map.of(APP, app.toString(), "missing", List.of("MEDICAL")));
        event("DocumentParsed", Map.of(APP, app.toString(), DOC, passport.toString(), "stage", "NEEDS_REVIEW",
                "type", "PASSPORT_FOREIGN", "resultId", UUID.randomUUID().toString(), "reasons", List.of()));
        event("DocumentParsed", Map.of(APP, app.toString(), DOC, patent.toString(), "stage", "PARSED",
                "type", "WORK_PATENT", "resultId", UUID.randomUUID().toString(), "reasons", List.of()));
    }

    private void event(String type, Map<String, Object> payload) {
        workplace.onEvent(UUID.randomUUID().toString(), type, node(payload));
    }

    private JsonNode node(Object o) {
        return json.valueToTree(o);
    }

    private static Map<String, Object> field(String name, String title, String value, double conf) {
        return Map.of("name", name, "title", title, "value", value, "raw", value, "page", 1,
                "bbox", List.of(10, 20, 200, 40), "confidence", conf, "checks", List.of());
    }

    private static RequestPostProcessor op(String user) {
        return jwt().jwt(j -> j.claim("preferred_username", user))
                .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    private static RequestPostProcessor senior() {
        return jwt().jwt(j -> j.claim("preferred_username", "senior"))
                .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"),
                        new SimpleGrantedAuthority("ROLE_SENIOR_OPERATOR"));
    }
}
