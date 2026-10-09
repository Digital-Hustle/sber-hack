package ru.documents.docproc.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.documents.docproc.features.processing.query.service.ResultQueryService;

/**
 * Метрика docproc_jobs{stage} (NFR-07), обновляется раз в 15 с.
 */
@Component
public class MetricsConfig {

    private static final List<String> STAGES = List.of("QUEUED", "RUNNING", "EXTRACTING_TEXT", "CLASSIFYING",
            "EXTRACTING_FIELDS", "VALIDATING", "PARSED", "NEEDS_REVIEW", "UNREADABLE", "WAITING_MODEL", "FAILED");

    private final ResultQueryService queries;
    private final Map<String, AtomicLong> values = new ConcurrentHashMap<>();

    public MetricsConfig(ResultQueryService queries, MeterRegistry meters) {
        this.queries = queries;
        for (String s : STAGES) {
            AtomicLong v = new AtomicLong();
            values.put(s, v);
            Gauge.builder("docproc_jobs", v, AtomicLong::get).tag("stage", s).register(meters);
        }
    }

    /** Обновить. */
    @Scheduled(fixedDelay = 15_000, initialDelay = 5_000)
    public void refresh() {
        Map<String, Long> c = queries.counts();
        values.forEach((k, v) -> v.set(c.getOrDefault(k, 0L)));
    }
}
