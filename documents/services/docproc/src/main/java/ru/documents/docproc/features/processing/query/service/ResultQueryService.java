package ru.documents.docproc.features.processing.query.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.documents.common.exception.DomainException;
import ru.documents.docproc.features.processing.model.DocType;
import ru.documents.docproc.features.processing.model.QueueStats;
import ru.documents.docproc.features.processing.model.ResultView;
import ru.documents.docproc.features.processing.model.ResultView.FieldView;
import ru.documents.docproc.features.processing.model.Stages;
import ru.documents.docproc.features.processing.query.repository.ResultReadRepository;

/**
 * Чтение результатов разбора и статистики очереди.
 */
@Service
public class ResultQueryService {

    private final ResultReadRepository repo;
    private final TypeRegistry types;
    private final ObjectMapper json;
    private final Clock clock;

    public ResultQueryService(ResultReadRepository repo, TypeRegistry types, ObjectMapper json, Clock clock) {
        this.repo = repo;
        this.types = types;
        this.json = json;
        this.clock = clock;
    }

    /** Результат документа: текущий итог или стадия, если разбор ещё идёт. */
    @Transactional(readOnly = true)
    public ResultView result(UUID doc) {
        Map<String, Object> job = repo.job(doc).orElseThrow(DomainException::notFound);
        String jobStage = (String) job.get("stage");
        Integer attempts = (Integer) job.get("attempts");
        String err = (String) job.get("last_error_code");
        Optional<Map<String, Object>> cur = repo.current(doc);
        if (cur.isEmpty()) {
            return new ResultView(doc, (UUID) job.get("application_id"), jobStage, null, null, null, null, null, List.of(),
                    null, null, null, null, null, null, null, attempts, err, null);
        }
        Map<String, Object> r = cur.get();
        String type = (String) r.get("type");
        String name = types.get(type).map(DocType::name).orElse(Stages.UNKNOWN.equals(type) ? "Не определён" : type);
        List<FieldView> fields = repo.fields((UUID) r.get("id")).stream().map(f -> new FieldView((String) f.get("name"),
                (String) f.get("title"), (String) f.get("value"), (String) f.get("raw"), (Integer) f.get("page"),
                node(f.get("bbox")), (Double) f.get("confidence"), node(f.get("checks")), (String) f.get("reason")))
                .toList();
        String stage = Stages.FINAL.contains(jobStage) ? (String) r.get("stage") : jobStage;
        return new ResultView(doc, (UUID) r.get("application_id"), stage, type, name, (Double) r.get("type_confidence"),
                node(r.get("evidence")), node(r.get("reasons")), fields, node(r.get("quality")), node(r.get("mrz")),
                node(r.get("pages")), (String) r.get("model"), (String) r.get("prompt_version"),
                (Integer) r.get("schema_version"), node(r.get("durations")), attempts, err,
                ((Timestamp) r.get("created_at")).toInstant());
    }

    /** Статистика очереди за сутки. */
    @Transactional(readOnly = true)
    public QueueStats stats() {
        Map<String, Long> c = repo.countsByStage();
        Instant since = clock.instant().minus(Duration.ofDays(1));
        Map<String, Double> avg = new LinkedHashMap<>();
        repo.avgStageMs(since).forEach((k, v) -> avg.put(k, Math.round(v / 100.0) / 10.0));
        long waiting = c.getOrDefault(Stages.QUEUED, 0L);
        long running = c.getOrDefault("RUNNING", 0L) + c.getOrDefault(Stages.EXTRACTING_TEXT, 0L)
                + c.getOrDefault(Stages.CLASSIFYING, 0L) + c.getOrDefault(Stages.EXTRACTING_FIELDS, 0L)
                + c.getOrDefault(Stages.VALIDATING, 0L);
        return new QueueStats(waiting, running, c.getOrDefault(Stages.PARSED, 0L), c.getOrDefault(Stages.NEEDS_REVIEW, 0L),
                c.getOrDefault(Stages.UNREADABLE, 0L), c.getOrDefault(Stages.WAITING_MODEL, 0L),
                c.getOrDefault(Stages.FAILED, 0L), avg, repo.p95Seconds(since));
    }

    /** Число заданий по стадиям (для метрики docproc_jobs). */
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        return repo.countsByStage();
    }

    private JsonNode node(Object s) {
        if (s == null) {
            return null;
        }
        try {
            return json.readTree(s.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
