package ru.documents.docproc.features.processing.application.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.documents.docproc.features.processing.command.service.JobCommandService;
import ru.documents.docproc.features.processing.gateway.DependencyException;
import ru.documents.docproc.features.processing.gateway.OcrRejectedException;
import ru.documents.docproc.features.processing.model.FinalResult;
import ru.documents.docproc.features.processing.model.Finding;
import ru.documents.docproc.features.processing.model.Job;
import ru.documents.docproc.features.processing.model.Stages;
import ru.documents.docproc.features.processing.model.StepOutcome;
import ru.documents.llm.api.ModelInvalidOutputException;
import ru.documents.llm.api.ModelUnavailableException;

/**
 * Машина стадий: выполняет шаги задания по порядку, каждый шаг восстанавливается из stage_outputs (FR-20, FR-26).
 * Внешние вызовы (intake, ocr, модель) — вне транзакций; запись результата шага — отдельной транзакцией.
 */
@Service
public class StageRunner {

    private static final Logger LOG = LoggerFactory.getLogger(StageRunner.class);

    private final JobCommandService commands;
    private final TextStage text;
    private final ClassifyStage classify;
    private final ExtractStage extract;
    private final ValidateStage validate;
    private final Results results;
    private final MeterRegistry meters;

    public StageRunner(JobCommandService commands, TextStage text, ClassifyStage classify, ExtractStage extract,
                       ValidateStage validate, Results results, MeterRegistry meters) {
        this.commands = commands;
        this.text = text;
        this.classify = classify;
        this.extract = extract;
        this.validate = validate;
        this.results = results;
        this.meters = meters;
    }

    /** Выполнить задание до итога, ожидания или потери аренды. */
    public void run(Job claimed) {
        Job job = claimed;
        Map<String, JsonNode> outputs = new java.util.HashMap<>(commands.outputs(job.documentId()));
        try {
            while (true) {
                String step = job.step();
                if (!commands.stageStarted(job, step)) {
                    return;
                }
                job = with(job, step, step);
                long t0 = System.nanoTime();
                StepOutcome o = execute(step, job, outputs);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                Timer.builder("docproc_stage_seconds").tag("stage", step).register(meters)
                        .record(java.time.Duration.ofMillis(ms));
                o.output().put("ms", ms);
                outputs.put(step, o.output());
                if (o.result() != null) {
                    commands.finish(job, withDuration(o.result(), step, ms));
                    return;
                }
                if (!commands.completeStep(job, step, o.output())) {
                    return;
                }
                job = with(job, Stages.next(step), step);
                if (o.output().path("vision").asBoolean(false)) {
                    job = new Job(job.id(), job.documentId(), job.applicationId(), job.step(), job.stage(), job.status(),
                            job.attempts(), job.worker(), job.forcedType(), true, job.createdAt(), job.startedAt());
                }
            }
        } catch (ModelUnavailableException e) {
            LOG.warn("model unavailable doc={} step={}", job.documentId(), job.step());
            commands.waitModel(job, "MODEL_UNAVAILABLE");
        } catch (ModelInvalidOutputException e) {
            LOG.warn("model invalid output doc={} step={}", job.documentId(), job.step());
            commands.finish(job, invalidOutput(job, outputs));
        } catch (OcrRejectedException e) {
            LOG.warn("ocr rejected doc={} code={}", job.documentId(), e.code());
            commands.finish(job, results.build(Stages.FAILED, Stages.UNKNOWN, null, List.of(), List.of(Finding.of(e.code())),
                    List.of(), outputs.get(Stages.EXTRACTING_TEXT), null, null, null, outputs));
        } catch (DependencyException e) {
            LOG.warn("dependency failed doc={} step={} code={}", job.documentId(), job.step(), e.code());
            failOrRetry(job, e.code(), outputs);
        } catch (RuntimeException e) {
            LOG.error("stage failed doc={} step={} error={}", job.documentId(), job.step(), e.getClass().getSimpleName(), e);
            failOrRetry(job, "INTERNAL_ERROR", outputs);
        }
    }

    private void failOrRetry(Job job, String code, Map<String, JsonNode> outputs) {
        if (!commands.retryLater(job, code)) {
            commands.finish(job, results.build(Stages.FAILED, Stages.UNKNOWN, null, List.of(), List.of(Finding.of(code)),
                    List.of(), outputs.get(Stages.EXTRACTING_TEXT), null, null, null, outputs));
        }
    }

    private FinalResult invalidOutput(Job job, Map<String, JsonNode> outputs) {
        JsonNode cls = outputs.get(Stages.CLASSIFYING);
        String type = cls == null ? Stages.UNKNOWN : cls.path("type").asText(Stages.UNKNOWN);
        Double conf = cls == null ? null : cls.path("confidence").asDouble();
        return results.build(Stages.NEEDS_REVIEW, type, conf, List.of(), List.of(Finding.of("MODEL_INVALID_OUTPUT")),
                List.of(), outputs.get(Stages.EXTRACTING_TEXT), null, Prompts.CLASSIFY + "+" + Prompts.EXTRACT, null, outputs);
    }

    private StepOutcome execute(String step, Job job, Map<String, JsonNode> outputs) {
        return switch (step) {
            case Stages.EXTRACTING_TEXT -> text.run(job);
            case Stages.CLASSIFYING -> classify.run(job, outputs);
            case Stages.EXTRACTING_FIELDS -> extract.run(job, outputs);
            case Stages.VALIDATING -> validate.run(outputs);
            default -> throw new IllegalStateException("step " + step);
        };
    }

    private static FinalResult withDuration(FinalResult r, String step, long ms) {
        Map<String, Long> d = new java.util.LinkedHashMap<>(r.durations());
        d.put(step, ms);
        return new FinalResult(r.stage(), r.type(), r.typeConfidence(), r.evidence(), r.findings(), r.fields(), r.quality(),
                r.mrz(), r.pages(), r.model(), r.promptVersion(), r.schemaVersion(), d);
    }

    private static Job with(Job j, String step, String stage) {
        return new Job(j.id(), j.documentId(), j.applicationId(), step, stage, j.status(), j.attempts(), j.worker(),
                j.forcedType(), j.vision(), j.createdAt(), j.startedAt());
    }
}
