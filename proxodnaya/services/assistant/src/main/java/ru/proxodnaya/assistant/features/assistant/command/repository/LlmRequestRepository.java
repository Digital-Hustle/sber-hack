package ru.proxodnaya.assistant.features.assistant.command.repository;

import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.assistant.features.assistant.model.LlmCall;
import ru.proxodnaya.assistant.features.assistant.model.Validation;
import ru.proxodnaya.assistant.jooq.Tables;

/**
 * Журнал вызовов модели (FR-46).
 */
@Repository
public class LlmRequestRepository {

    private static final int MAX_ERROR = 500;

    private final DSLContext dsl;

    public LlmRequestRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Записывает вызов.
     *
     * @param call вызов
     */
    public void insert(LlmCall call) {
        var table = Tables.LLM_REQUESTS;
        String error = call.error() == null ? null : call.error().substring(0, Math.min(call.error().length(), MAX_ERROR));
        dsl.insertInto(table)
                .set(table.ID, UuidCreator.getTimeOrderedEpoch())
                .set(table.ASK_ID, call.askId())
                .set(table.TS, call.ts().atOffset(ZoneOffset.UTC))
                .set(table.PROVIDER, call.provider())
                .set(table.MODEL, call.model())
                .set(table.LATENCY_MS, (int) call.latencyMs())
                .set(table.TOKENS_IN, call.tokensIn())
                .set(table.TOKENS_OUT, call.tokensOut())
                .set(table.TOOL_CALLS, call.toolCalls())
                .set(table.VALIDATION, call.validation() == null ? null : call.validation().name())
                .set(table.ERROR, error)
                .set(table.QUESTION_HASH, call.questionHash())
                .execute();
    }

    /**
     * Итог проверки чисел — всем вызовам вопроса.
     *
     * @param askId   вопрос
     * @param verdict итог
     */
    public void verdict(UUID askId, Validation verdict) {
        var table = Tables.LLM_REQUESTS;
        dsl.update(table).set(table.VALIDATION, verdict.name()).where(table.ASK_ID.eq(askId)).execute();
    }
}
