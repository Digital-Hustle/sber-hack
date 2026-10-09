package ru.documents.docproc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ru.documents.docproc.features.processing.application.service.PipelineWorker;
import ru.documents.docproc.features.processing.command.service.JobCommandService;
import ru.documents.docproc.features.processing.gateway.IntakeGateway;
import ru.documents.docproc.features.processing.gateway.OcrGateway;
import ru.documents.docproc.features.processing.model.FileRef;
import ru.documents.docproc.messaging.consumer.IntakeEventsConsumer;
import ru.documents.llm.api.LlmGateway;
import ru.documents.llm.api.LlmRequest;
import ru.documents.llm.api.LlmResult;
import ru.documents.llm.api.LlmStatus;
import ru.documents.llm.api.ModelUnavailableException;

/**
 * Очередь и стадии на настоящем PostgreSQL; зависимости (intake, ocr, модель) подменены в тесте.
 * FR-20 (очередь, параллельный захват, рестарт посреди стадии), FR-26 (WAITING_MODEL), FR-21..24.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PipelineIntegrationTest {

    private static final String SNILS_TEXT = "СТРАХОВОЕ СВИДЕТЕЛЬСТВО обязательного пенсионного страхования 112-233-445 95 "
            + "ПЕТРОВА АННА СЕРГЕЕВНА дата рождения 01.02.1990";

    @Autowired
    private IntakeEventsConsumer consumer;
    @Autowired
    private PipelineWorker worker;
    @Autowired
    private JobCommandService commands;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IntakeGateway intake;
    @MockitoBean
    private OcrGateway ocr;
    @MockitoBean
    private LlmGateway llm;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("update jobs set status = 'DONE' where status in ('READY', 'RUNNING')").update();
        when(intake.documentFiles(any())).thenReturn(new IntakeGateway.DocumentFiles(
                List.of(new FileRef(UUID.randomUUID(), "image/png", 1)), List.of()));
        when(intake.content(any())).thenReturn(new byte[] {1, 2, 3});
        when(ocr.extract(any())).thenReturn(ocrPage(SNILS_TEXT, 0.92, 300.0));
        when(llm.status()).thenReturn(new LlmStatus("ollama", "qwen3:8b", null, true, Instant.now(), null));
        when(llm.structured(any())).thenAnswer(inv -> answer(inv.getArgument(0)));
    }

    private LlmResult answer(LlmRequest rq) throws Exception {
        if ("CLASSIFYING".equals(rq.stage())) {
            return result("{\"type\":\"SNILS\",\"confidence\":0.95,\"evidence\":[\"СТРАХОВОЕ СВИДЕТЕЛЬСТВО\"]}");
        }
        return result("""
                {"fullName":{"value":"Петрова Анна Сергеевна","raw":"ПЕТРОВА АННА СЕРГЕЕВНА","page":1,"confidence":0.95,"reason":null},
                 "snils":{"value":"112-233-445 95","raw":"112-233-445 95","page":1,"confidence":0.97,"reason":null},
                 "birthDate":{"value":"01.02.1990","raw":"01.02.1990","page":1,"confidence":0.95,"reason":null},
                 "registrationDate":{"value":null,"raw":null,"page":null,"confidence":0,"reason":"нет в документе"}}""");
    }

    private LlmResult result(String s) throws Exception {
        return new LlmResult(om.readTree(s), "ollama", "qwen3:8b", 10, 100, 50, 1);
    }

    private JsonNode ocrPage(String text, double conf, double blur) {
        ObjectNode r = om.createObjectNode().put("mime", "image/png").put("durationMs", 5);
        ObjectNode p = r.putArray("pages").addObject().put("n", 1).put("width", 1700).put("height", 1100)
                .put("textLayer", false).put("text", text).put("meanConf", conf);
        var words = p.putArray("words");
        int x = 10;
        for (String w : text.split(" ")) {
            words.addObject().put("t", w).put("conf", conf).putArray("bbox").add(x).add(100).add(x + 60).add(130);
            x += 70;
        }
        p.putObject("quality").put("blur", blur).put("brightness", 0.9).put("glare", 0.0).put("cutOff", false).put("dpi", 0);
        return r;
    }

    private UUID received() {
        UUID doc = UUID.randomUUID();
        ObjectNode p = om.createObjectNode().put("documentId", doc.toString())
                .put("applicationId", UUID.randomUUID().toString()).put("version", 1);
        consumer.handle(UUID.randomUUID().toString(), "DocumentReceived", p);
        return doc;
    }

    private String stage(UUID doc) {
        return jdbc.sql("select stage from jobs where document_id = :d").param("d", doc).query(String.class).single();
    }

    private void drain() {
        while (worker.once("test")) {
            Thread.onSpinWait();
        }
    }

    @Test
    void parsesDocumentThroughAllStages() throws Exception {
        UUID doc = received();
        assertThat(stage(doc)).isEqualTo("QUEUED");
        drain();
        assertThat(stage(doc)).isEqualTo("PARSED");

        String body = mvc.perform(get("/api/v1/documents/" + doc + "/result")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SNILS"))
                .andReturn().getResponse().getContentAsString();
        JsonNode r = om.readTree(body);
        JsonNode snils = null;
        for (JsonNode f : r.path("fields")) {
            if ("snils".equals(f.path("name").asText())) {
                snils = f;
            }
        }
        assertThat(snils).isNotNull();
        assertThat(snils.path("value").asText()).isEqualTo("112-233-445 95");
        assertThat(snils.path("bbox").size()).isEqualTo(4);
        assertThat(snils.path("checks").get(0).path("ok").asBoolean()).isTrue();
        assertThat(r.path("promptVersion").asText()).isEqualTo("classify.v1+extract.v1");

        List<String> events = jdbc.sql("select payload->>'type' from outbox where payload->'payload'->>'documentId' = :d "
                + "order by created_at").param("d", doc.toString()).query(String.class).list();
        assertThat(events).contains("StageChanged", "DocumentParsed");
        Integer calls = jdbc.sql("select count(*) from llm_calls where document_id = :d").param("d", doc.toString())
                .query(Integer.class).single();
        assertThat(calls).isNotNull();
    }

    @Test
    void removedDocumentLosesTextAndResults() throws Exception {
        UUID doc = received();
        drain();
        assertThat(stage(doc)).isEqualTo("PARSED");
        consumer.handle(UUID.randomUUID().toString(), "DocumentRemoved", om.createObjectNode()
                .put("documentId", doc.toString()).put("applicationId", UUID.randomUUID().toString()));
        assertThat(jdbc.sql("select status from jobs where document_id = :d").param("d", doc).query(String.class)
                .single()).isEqualTo("CANCELLED");
        assertThat(jdbc.sql("""
                select (select count(*) from stage_outputs where document_id = :d)
                     + (select count(*) from parse_results where document_id = :d)""").param("d", doc)
                .query(Long.class).single()).isZero();
        mvc.perform(get("/api/v1/documents/" + doc + "/result")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void multiDocumentFileIsSplitByPages() throws Exception {
        JsonNode two = ocrPage(SNILS_TEXT, 0.92, 300.0);
        ObjectNode second = ((ObjectNode) two.path("pages").get(0)).deepCopy().put("n", 2)
                .put("text", "СВИДЕТЕЛЬСТВО о постановке на учёт ИНН 500100732259");
        ((com.fasterxml.jackson.databind.node.ArrayNode) two.path("pages")).add(second);
        when(ocr.extract(any())).thenReturn(two);
        List<String> extractPrompts = new java.util.ArrayList<>();
        doAnswer(inv -> {
            LlmRequest rq = inv.getArgument(0);
            if ("CLASSIFYING".equals(rq.stage())) {
                assertThat(rq.schema().path("required").toString()).contains("segments");
                return result("{\"type\":\"SNILS\",\"confidence\":0.95,\"evidence\":[\"СТРАХОВОЕ СВИДЕТЕЛЬСТВО\"],"
                        + "\"segments\":[{\"type\":\"SNILS\",\"pages\":[1]},{\"type\":\"INN\",\"pages\":[2]}]}");
            }
            extractPrompts.add(rq.user());
            return answer(rq);
        }).when(llm).structured(any());
        UUID doc = received();
        drain();
        assertThat(stage(doc)).isEqualTo("PARSED"); // MULTI_DOCUMENT — справка, остальное выделит intake
        assertThat(extractPrompts).hasSize(1);
        assertThat(extractPrompts.get(0)).contains("Страница 1").doesNotContain("Страница 2").doesNotContain("500100732259");
        String body = mvc.perform(get("/api/v1/documents/" + doc + "/result")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("SNILS"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("MULTI_DOCUMENT").contains("стр. 2");
        assertThat(om.readTree(body).path("promptVersion").asText()).startsWith("classify.v2");
    }

    @Test
    void parsedEventCarriesSplitAndChildParsesOnlyItsPages() throws Exception {
        JsonNode two = ocrPage(SNILS_TEXT, 0.92, 300.0);
        ObjectNode second = ((ObjectNode) two.path("pages").get(0)).deepCopy().put("n", 2)
                .put("text", "СВИДЕТЕЛЬСТВО о постановке на учёт физического лица в налоговом органе ИНН 500100732259");
        ((com.fasterxml.jackson.databind.node.ArrayNode) two.path("pages")).add(second);
        when(ocr.extract(any())).thenReturn(two);
        List<String> prompts = new java.util.ArrayList<>();
        doAnswer(inv -> {
            LlmRequest rq = inv.getArgument(0);
            prompts.add(rq.stage() + ":" + rq.user());
            if ("CLASSIFYING".equals(rq.stage()) && rq.user().contains("Страница 1")) {
                return result("{\"type\":\"SNILS\",\"confidence\":0.95,\"evidence\":[\"СТРАХОВОЕ СВИДЕТЕЛЬСТВО\"],"
                        + "\"segments\":[{\"type\":\"SNILS\",\"pages\":[1]},{\"type\":\"INN\",\"pages\":[2]}]}");
            }
            if ("CLASSIFYING".equals(rq.stage())) {
                return result("{\"type\":\"INN\",\"confidence\":0.95,\"evidence\":[\"ИНН 500100732259\"]}");
            }
            return answer(rq);
        }).when(llm).structured(any());
        UUID source = received();
        drain();
        String split = jdbc.sql("select payload->'payload'->>'split' from outbox where payload->>'type' = 'DocumentParsed' "
                + "and payload->'payload'->>'documentId' = :d").param("d", source.toString()).query(String.class).single();
        assertThat(om.readTree(split).get(0).path("type").asText()).isEqualTo("INN");
        assertThat(om.readTree(split).get(0).path("pages").toString()).isEqualTo("[2]");

        // intake выделил документ: те же файлы, только страница 2
        when(intake.documentFiles(any())).thenReturn(new IntakeGateway.DocumentFiles(
                List.of(new FileRef(UUID.randomUUID(), "image/png", 2)), List.of(2)));
        prompts.clear();
        UUID child = received();
        drain();
        assertThat(prompts).isNotEmpty().allSatisfy(p -> assertThat(p).doesNotContain("СТРАХОВОЕ"));
        assertThat(prompts.get(0)).startsWith("CLASSIFYING:").doesNotContain("Страница 1");
        mvc.perform(get("/api/v1/documents/" + child + "/result")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("INN"));
    }

    @Test
    void duplicateEventCreatesOneJob() {
        UUID doc = UUID.randomUUID();
        ObjectNode p = om.createObjectNode().put("documentId", doc.toString())
                .put("applicationId", UUID.randomUUID().toString());
        String eventId = UUID.randomUUID().toString();
        consumer.handle(eventId, "DocumentReceived", p);
        consumer.handle(eventId, "DocumentReceived", p);
        consumer.handle(UUID.randomUUID().toString(), "DocumentReceived", p);
        assertThat(jdbc.sql("select count(*) from jobs where document_id = :d").param("d", doc).query(Integer.class)
                .single()).isEqualTo(1);
    }

    @Test
    void waitingModelThenResumesAutomatically() throws Exception {
        doThrow(new ModelUnavailableException("down", null)).when(llm).structured(any());
        UUID doc = received();
        worker.once("test");
        assertThat(stage(doc)).isEqualTo("WAITING_MODEL");
        Integer attempts = jdbc.sql("select attempts from jobs where document_id = :d").param("d", doc)
                .query(Integer.class).single();
        assertThat(attempts).isZero();
        assertThat(jdbc.sql("select count(*) from stage_outputs where document_id = :d and stage = 'EXTRACTING_TEXT'")
                .param("d", doc).query(Integer.class).single()).isEqualTo(1);

        doAnswer(inv -> answer(inv.getArgument(0))).when(llm).structured(any());
        drain();
        assertThat(stage(doc)).isEqualTo("PARSED");
    }

    @Test
    void restartMidStageContinuesFromSavedOutput() {
        UUID doc = received();
        var job = commands.claim("crashed").orElseThrow();
        commands.stageStarted(job, "EXTRACTING_TEXT");
        jdbc.sql("update jobs set lease_until = now() - interval '1 minute' where document_id = :d").param("d", doc).update();
        drain();
        assertThat(stage(doc)).isEqualTo("PARSED");
    }

    @Test
    void concurrentWorkersNeverTakeTheSameJob() throws Exception {
        for (int i = 0; i < 6; i++) {
            received();
        }
        List<UUID> taken = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 6; i++) {
            String w = "w" + i;
            pool.submit(() -> {
                start.await();
                commands.claim(w).ifPresent(j -> taken.add(j.id()));
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(taken).doesNotHaveDuplicates().hasSize(6);
    }

    @Test
    void blurryPhotoWithoutTextIsUnreadable() {
        when(ocr.extract(any())).thenReturn(ocrPage("ыв", 0.2, 3.0));
        UUID doc = received();
        drain();
        assertThat(stage(doc)).isEqualTo("UNREADABLE");
        String reason = jdbc.sql("select reasons->0->>'code' from parse_results where document_id = :d and current")
                .param("d", doc).query(String.class).single();
        assertThat(reason).isEqualTo("IMAGE_BLURRY");
    }

    @Test
    void lowTypeConfidenceGoesToOperator() {
        doAnswer(inv -> result("{\"type\":\"INN\",\"confidence\":0.3,\"evidence\":[]}")).when(llm).structured(any());
        UUID doc = received();
        drain();
        assertThat(stage(doc)).isEqualTo("NEEDS_REVIEW");
        String type = jdbc.sql("select type from parse_results where document_id = :d and current").param("d", doc)
                .query(String.class).single();
        assertThat(type).isEqualTo("UNKNOWN");
    }

    @Test
    void reprocessRequiresSeniorOperator() throws Exception {
        UUID doc = received();
        mvc.perform(post("/api/v1/documents/" + doc + "/reprocess")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/documents/" + doc + "/reprocess")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SENIOR_OPERATOR"))))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/queue/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/docproc/documents/" + doc + "/result")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/model/status").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("qwen3:8b"));
    }
}
