package ru.proxodnaya.assistant.features.assistant.query.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.assistant.features.assistant.model.LlmStatus;
import ru.proxodnaya.assistant.features.assistant.query.repository.LlmStatusRepository;
import ru.proxodnaya.assistant.jooq.tables.records.LlmRequestsRecord;
import ru.proxodnaya.assistant.llm.LlmGateway;

/**
 * Состояние помощника (FR-46): провайдер, модель, последние вызовы.
 */
@Service
public class LlmStatusQueryService {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final LlmStatusRepository repository;
    private final LlmGateway llm;
    private final Clock clock;

    public LlmStatusQueryService(LlmStatusRepository repository, LlmGateway llm, Clock clock) {
        this.repository = repository;
        this.llm = llm;
        this.clock = clock;
    }

    /**
     * Состояние.
     *
     * @return сводка
     */
    @Transactional(readOnly = true)
    public LlmStatus status() {
        LlmRequestsRecord last = repository.last();
        LlmStatusRepository.Window window = repository.since(clock.instant().minus(WINDOW));
        return new LlmStatus(llm.provider(), llm.model(), last == null ? null : last.getTs().toInstant(),
                last != null && last.getError() == null, last == null ? null : last.getError(), window.calls(),
                window.avgLatencyMs(), window.refused());
    }
}
