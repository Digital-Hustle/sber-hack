package ru.proxodnaya.access.features.pass.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.pass.command.service.JournalService;
import ru.proxodnaya.access.features.pass.command.service.PresenceService;
import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.features.qr.api.QrUses;
import ru.proxodnaya.access.messaging.outbox.Outbox;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.common.event.Topics;

/**
 * Короткая транзакция решения (инвариант Д2): гашение QR + журнал + присутствие + outbox {@code PassDecided}.
 * Внешних вызовов здесь нет — они сделаны до неё.
 */
@Service
public class PassRecorder {

    private final QrUses qrUses;
    private final JournalService journal;
    private final PresenceService presence;
    private final Outbox outbox;

    public PassRecorder(QrUses qrUses, JournalService journal, PresenceService presence, Outbox outbox) {
        this.qrUses = qrUses;
        this.journal = journal;
        this.presence = presence;
        this.outbox = outbox;
    }

    /**
     * Фиксирует итог шага кадров. QR гасится при любом итоге этого шага: один QR — один челлендж.
     * Если QR уже погашен (тот же QR на другом киоске), итог заменяется на {@code QR_REUSED}.
     *
     * @param record итог, посчитанный DecisionEngine
     * @return записанный итог
     */
    @Transactional
    public PassRecord finish(PassRecord record) {
        boolean consumed = qrUses.consume(record.deviceId(), record.window(), record.id(), record.ts());
        PassRecord result = consumed ? record : reused(record);
        write(result);
        return result;
    }

    /**
     * Фиксирует отказ без гашения QR (отказ до челленджа или истёкший челлендж).
     *
     * @param record отказ
     * @return записанный итог
     */
    @Transactional
    public PassRecord deny(PassRecord record) {
        write(record);
        return record;
    }

    private void write(PassRecord rec) {
        journal.append(rec);
        if (rec.decision() == Decision.ALLOW) {
            presence.apply(rec.workerId(), rec.direction(), rec.ts());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attemptId", rec.id().toString());
        payload.put("workerId", rec.workerId());
        payload.put("checkpointId", rec.checkpointId());
        payload.put("zoneId", null);
        payload.put("direction", rec.direction().name());
        payload.put("decision", rec.decision().name());
        payload.put("reasonCode", rec.code().name());
        payload.put("ts", rec.ts().toString());
        payload.put("source", rec.source().name());
        outbox.add(Topics.ACCESS_PASS_V1, "PassDecided", rec.workerId() == null ? rec.id().toString() : rec.workerId(), payload);
    }

    private static PassRecord reused(PassRecord rec) {
        Map<String, Object> details = new LinkedHashMap<>(rec.details());
        details.put("computedCode", rec.code().name());
        return new PassRecord(rec.id(), rec.ts(), rec.checkpointId(), rec.direction(), rec.workerId(), rec.deviceId(), rec.window(),
                rec.faceScore(), rec.livenessOk(), rec.action(), Decision.DENY, ReasonCode.QR_REUSED, rec.latencyMs(),
                rec.source(), details);
    }
}
