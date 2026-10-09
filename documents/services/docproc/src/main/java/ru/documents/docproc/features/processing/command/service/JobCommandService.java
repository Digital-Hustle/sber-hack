package ru.documents.docproc.features.processing.command.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.documents.common.event.Topics;
import ru.documents.common.exception.DomainException;
import ru.documents.common.outbox.OutboxWriter;
import ru.documents.docproc.config.DocprocProperties;
import ru.documents.docproc.features.processing.command.repository.JobRepository;
import ru.documents.docproc.features.processing.command.repository.OutputRepository;
import ru.documents.docproc.features.processing.command.repository.ResultRepository;
import ru.documents.docproc.features.processing.model.FieldResult;
import ru.documents.docproc.features.processing.model.FinalResult;
import ru.documents.docproc.features.processing.model.Finding;
import ru.documents.docproc.features.processing.model.Job;
import ru.documents.docproc.features.processing.model.Stages;

/**
 * Изменения очереди и результатов; каждый переход — запись + событие в outbox одной транзакцией.
 */
@Service
public class JobCommandService {

    private static final String DOC = "documentId";
    private static final String APP = "applicationId";
    private static final String STAGE = "stage";

    private final JobRepository jobs;
    private final OutputRepository outputs;
    private final ResultRepository results;
    private final OutboxWriter outbox;
    private final ObjectMapper json;
    private final Clock clock;
    private final DocprocProperties props;

    public JobCommandService(JobRepository jobs, OutputRepository outputs, ResultRepository results, OutboxWriter outbox,
                             ObjectMapper json, Clock clock, DocprocProperties props) {
        this.jobs = jobs;
        this.outputs = outputs;
        this.results = results;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
        this.props = props;
    }

    /** Новый документ из intake (идемпотентно). */
    @Transactional
    public void enqueue(UUID doc, UUID app, int version) {
        Instant now = clock.instant();
        if (jobs.insert(UuidCreator.getTimeOrderedEpoch(), doc, app, version, Stages.EXTRACTING_TEXT, Stages.QUEUED, now)) {
            outputs.event(doc, app, Stages.QUEUED, null, null, now);
            stageChanged(doc, app, Stages.QUEUED, null, null, null);
        }
    }

    /** Файлы документа изменились — разбор заново. */
    @Transactional
    public void filesChanged(UUID doc, UUID app, int version) {
        Instant now = clock.instant();
        if (jobs.byDocument(doc).isEmpty()) {
            enqueue(doc, app, version);
            return;
        }
        outputs.deleteExcept(doc, List.of());
        jobs.restart(doc, Stages.EXTRACTING_TEXT, Stages.QUEUED, null, version, now);
        outputs.event(doc, app, Stages.QUEUED, null, null, now);
        stageChanged(doc, app, Stages.QUEUED, null, null, null);
    }

    /** Документ удалён соискателем. */
    @Transactional
    public void removed(UUID doc) {
        jobs.cancel(doc, clock.instant());
    }

    /** Разобрать заново (FR-30): прошлые результаты остаются в истории. */
    @Transactional
    public void reprocess(UUID doc) {
        Job job = jobs.byDocument(doc).orElseThrow(DomainException::notFound);
        Instant now = clock.instant();
        outputs.deleteExcept(doc, List.of());
        jobs.restart(doc, Stages.EXTRACTING_TEXT, Stages.QUEUED, null, null, now);
        outputs.event(doc, job.applicationId(), Stages.QUEUED, null, null, now);
        stageChanged(doc, job.applicationId(), Stages.QUEUED, null, null, null);
    }

    /** Оператор сменил тип — переизвлечение полей по новой схеме, текст не распознаём заново. */
    @Transactional
    public void retype(UUID doc, String type) {
        Job job = jobs.byDocument(doc).orElseThrow(DomainException::notFound);
        Instant now = clock.instant();
        boolean haveText = outputs.load(doc).containsKey(Stages.EXTRACTING_TEXT);
        outputs.deleteExcept(doc, haveText ? List.of(Stages.EXTRACTING_TEXT) : List.of());
        jobs.restart(doc, haveText ? Stages.CLASSIFYING : Stages.EXTRACTING_TEXT, Stages.QUEUED, type, null, now);
        outputs.event(doc, job.applicationId(), Stages.QUEUED, null, null, now);
        stageChanged(doc, job.applicationId(), Stages.QUEUED, null, null, null);
    }

    /** Захватить задание. */
    @Transactional
    public Optional<Job> claim(String worker) {
        Instant now = clock.instant();
        return jobs.claim(worker, now, now.plus(props.lease()));
    }

    /** Продлить аренду. */
    @Transactional
    public boolean extend(UUID id, String worker) {
        return jobs.extend(id, worker, clock.instant().plus(props.lease()));
    }

    /** Выходы стадий документа. */
    @Transactional(readOnly = true)
    public Map<String, JsonNode> outputs(UUID doc) {
        Map<String, JsonNode> out = new HashMap<>();
        outputs.load(doc).forEach((k, v) -> out.put(k, read(v)));
        return out;
    }

    /** Видимая стадия меняется (начало шага). */
    @Transactional
    public boolean stageStarted(Job job, String stage) {
        if (!jobs.lockOwned(job.id(), job.worker())) {
            return false;
        }
        if (!stage.equals(job.stage())) {
            Instant now = clock.instant();
            jobs.setStage(job.id(), stage, now);
            outputs.event(job.documentId(), job.applicationId(), stage, null, null, now);
            stageChanged(job.documentId(), job.applicationId(), stage, null, null, null);
        }
        return true;
    }

    /** Шаг выполнен: выход сохранён, задание переходит к следующему шагу. */
    @Transactional
    public boolean completeStep(Job job, String step, ObjectNode output) {
        if (!jobs.lockOwned(job.id(), job.worker())) {
            return false;
        }
        Instant now = clock.instant();
        outputs.save(job.documentId(), step, output.toString(), now);
        jobs.advance(job.id(), Stages.next(step), now);
        if (output.path("vision").asBoolean(false)) {
            jobs.setVision(job.id(), true);
        }
        return true;
    }

    /** Модель недоступна: ждём без траты попыток (FR-26). */
    @Transactional
    public void waitModel(Job job, String error) {
        if (!jobs.lockOwned(job.id(), job.worker())) {
            return;
        }
        Instant now = clock.instant();
        jobs.postpone(job.id(), now.plus(props.waitingModelRetry()), false, error, now);
        if (!Stages.WAITING_MODEL.equals(job.stage())) {
            jobs.setStage(job.id(), Stages.WAITING_MODEL, now);
            outputs.event(job.documentId(), job.applicationId(), Stages.WAITING_MODEL, "MODEL_UNAVAILABLE", null, now);
            stageChanged(job.documentId(), job.applicationId(), Stages.WAITING_MODEL, null, "MODEL_UNAVAILABLE", null);
        }
    }

    /**
     * Сбой зависимости: повтор с экспоненциальной паузой; после JOB_MAX_ATTEMPTS — FAILED.
     *
     * @return true, если задание отложено, false — попытки исчерпаны (вызывающий завершает FAILED)
     */
    @Transactional
    public boolean retryLater(Job job, String code) {
        if (!jobs.lockOwned(job.id(), job.worker())) {
            return true;
        }
        if (job.attempts() + 1 >= props.maxAttempts()) {
            return false;
        }
        Instant now = clock.instant();
        long pause = Math.min(300, 5L << Math.min(job.attempts(), 6));
        jobs.postpone(job.id(), now.plus(Duration.ofSeconds(pause)), true, code, now);
        return true;
    }

    /** Итог разбора: результат, поля, стадия и события. */
    @Transactional
    public boolean finish(Job job, FinalResult r) {
        if (!jobs.lockOwned(job.id(), job.worker())) {
            return false;
        }
        Instant now = clock.instant();
        UUID id = UuidCreator.getTimeOrderedEpoch();
        results.retire(job.documentId());
        results.insert(id, job.documentId(), job.applicationId(), r.stage(), r.type(), r.typeConfidence(),
                write(r.evidence()), write(r.findings()), write(r.quality()), write(r.mrz()), write(r.pages()), r.model(),
                r.promptVersion(), r.schemaVersion(), write(r.durations()), now);
        int sort = 0;
        for (FieldResult f : r.fields()) {
            results.field(id, f.name(), f.title(), sort++, f.value(), f.raw(), f.page(), write(f.bbox()), f.confidence(),
                    write(f.checks()), f.reason());
        }
        jobs.finish(job.id(), r.stage(), now);
        Finding first = r.findings().isEmpty() ? null : r.findings().get(0);
        String reason = first == null ? null : first.code();
        long total = r.durations().values().stream().mapToLong(Long::longValue).sum();
        outputs.event(job.documentId(), job.applicationId(), r.stage(), reason, total, now);
        stageChanged(job.documentId(), job.applicationId(), r.stage(), r.type(), reason,
                first == null ? null : first.params());
        Map<String, Object> p = new HashMap<>();
        p.put(DOC, job.documentId().toString());
        p.put(APP, job.applicationId().toString());
        p.put(STAGE, r.stage());
        p.put("resultId", id.toString());
        p.put("type", r.type());
        if (r.typeConfidence() != null) {
            p.put("typeConfidence", r.typeConfidence());
        }
        p.put("reasons", r.findings());
        p.put("fields", r.fields().stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", f.name());
            m.put("value", f.value());
            m.put("confidence", f.confidence());
            return m;
        }).toList());
        if (r.model() != null) {
            p.put("model", r.model());
            p.put("promptVersion", r.promptVersion());
        }
        if (!Stages.UNREADABLE.equals(r.stage()) && !Stages.FAILED.equals(r.stage())) {
            List<Map<String, Object>> split = split(job.documentId(), r.type());
            if (!split.isEmpty()) {
                p.put("split", split);
            }
        }
        String type = switch (r.stage()) {
            case Stages.UNREADABLE -> "DocumentUnreadable";
            case Stages.FAILED -> "DocumentFailed";
            default -> "DocumentParsed";
        };
        outbox.add(Topics.DOCPROC_DOCUMENT_V1, type, job.applicationId().toString(), p);
        return true;
    }

    /**
     * Другие документы, найденные в многостраничном файле (FR-07, DP-14): intake выделит их в отдельные документы.
     * Первый сегмент — основной документ, остальные — к выделению.
     */
    private List<Map<String, Object>> split(UUID doc, String type) {
        String cls = outputs.load(doc).get(Stages.CLASSIFYING);
        if (cls == null || type == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (JsonNode seg : read(cls).path("segments")) {
            String t = seg.path("type").asText(null);
            if (t == null || t.equals(type) || Stages.UNKNOWN.equals(t) || !seg.path("pages").isArray()) {
                continue;
            }
            List<Integer> pages = new java.util.ArrayList<>();
            seg.path("pages").forEach(n -> pages.add(n.asInt()));
            out.add(Map.of("type", t, "pages", pages));
        }
        return out;
    }

    private void stageChanged(UUID doc, UUID app, String stage, String type, String reason, Map<String, String> params) {
        Map<String, Object> p = new HashMap<>();
        p.put(DOC, doc.toString());
        p.put(APP, app.toString());
        p.put(STAGE, stage);
        if (type != null) {
            p.put("type", type);
        }
        if (reason != null) {
            p.put("reasonCode", reason);
        }
        if (params != null && !params.isEmpty()) {
            p.put("reasonParams", params);
        }
        outbox.add(Topics.DOCPROC_DOCUMENT_V1, "StageChanged", app.toString(), p);
    }

    private JsonNode read(String s) {
        try {
            return json.readTree(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String write(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
