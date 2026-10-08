package ru.proxodnaya.access.api.mapper;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.api.dto.response.ChallengeRs;
import ru.proxodnaya.access.api.dto.response.DecisionRs;
import ru.proxodnaya.access.api.dto.response.JournalVerifyRs;
import ru.proxodnaya.access.api.dto.response.ScanRs;
import ru.proxodnaya.access.api.dto.response.WorkerRs;
import ru.proxodnaya.access.features.pass.model.JournalCheck;
import ru.proxodnaya.access.features.pass.model.PassOutcome;
import ru.proxodnaya.access.features.pass.model.PendingAttempt;
import ru.proxodnaya.access.features.pass.model.ScanOutcome;
import ru.proxodnaya.access.features.pass.model.WorkerPreview;

/**
 * Модели прохода → DTO. Вручную: ответ скана полиморфный, MapStruct тут только мешает.
 */
@Component
public class PassApiMapper {

    /**
     * Ответ на скан.
     *
     * @param outcome итог скана
     * @param now     время ответа (для timeoutMs)
     * @return DTO
     */
    public ScanRs convert(ScanOutcome outcome, Instant now) {
        if (!outcome.isChallenge()) {
            return new ScanRs("decision", convert(outcome.decision()), null, null, null);
        }
        PendingAttempt pending = outcome.challenge();
        long timeoutMs = Math.max(0, Duration.between(now, pending.expiresAt()).toMillis());
        return new ScanRs("challenge", null, pending.token().toString(),
                new ChallengeRs(pending.action().wire(), pending.action().textRu(), timeoutMs), convert(outcome.worker()));
    }

    /**
     * Решение.
     *
     * @param pass итог
     * @return DTO
     */
    public DecisionRs convert(PassOutcome pass) {
        return new DecisionRs(pass.attemptId().toString(), pass.decision().name(), pass.code().name(),
                pass.code().message(), pass.code().hint(),
                convert(pass.worker()), pass.direction().name(), pass.score(), pass.ts());
    }

    /**
     * Проверка журнала.
     *
     * @param check результат
     * @return DTO
     */
    public JournalVerifyRs convert(JournalCheck check) {
        return new JournalVerifyRs(check.ok(), check.checked(), check.brokenAt() == null ? null : check.brokenAt().toString());
    }

    private static WorkerRs convert(WorkerPreview worker) {
        return worker == null ? null : new WorkerRs(worker.id(), worker.fullName(), worker.position(), worker.contractor());
    }
}
