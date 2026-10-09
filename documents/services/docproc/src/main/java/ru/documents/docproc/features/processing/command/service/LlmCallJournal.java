package ru.documents.docproc.features.processing.command.service;

import java.time.Clock;

import org.springframework.stereotype.Component;

import ru.documents.docproc.features.processing.command.repository.LlmCallRepository;
import ru.documents.llm.api.LlmCallListener;
import ru.documents.llm.api.LlmCallRecord;

/**
 * Каждый вызов модели — строка в llm_calls (FR-43); без текста документа.
 */
@Component
public class LlmCallJournal implements LlmCallListener {

    private final LlmCallRepository repo;
    private final Clock clock;

    public LlmCallJournal(LlmCallRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    public void onCall(LlmCallRecord call) {
        repo.insert(call, clock.instant());
    }
}
