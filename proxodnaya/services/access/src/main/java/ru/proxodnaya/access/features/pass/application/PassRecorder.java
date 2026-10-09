package ru.proxodnaya.access.features.pass.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.config.properties.AccessProperties;
import ru.proxodnaya.access.features.pass.command.service.JournalService;
import ru.proxodnaya.access.features.pass.command.service.PresenceService;
import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.features.qr.api.QrUses;
import ru.proxodnaya.access.messaging.outbox.Outbox;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.common.event.Topics;

/**
 * Короткая транзакция решения (инвариант Д2): гашение QR + anti-passback + журнал + присутствие + outbox {@code PassDecided}.
 * Внешних вызовов здесь нет — они сделаны до неё. Порядок блокировок во всех методах один: QR → строка присутствия → журнал.
 */
@Service
public class PassRecorder {

    private final QrUses qrUses;
    private final JournalService journal;
    private final PresenceService presence;
    private final Outbox outbox;
    private final AccessProperties props;

    public PassRecorder(QrUses qrUses, JournalService journal, PresenceService presence, Outbox outbox, AccessProperties props) {
        this.qrUses = qrUses;
        this.journal = journal;
        this.presence = presence;
        this.outbox = outbox;
        this.props = props;
    }

    /**
     * Фиксирует итог шага кадров. QR гасится при любом итоге этого шага: один QR — один челлендж.
     * Если QR уже погашен (тот же QR на другом киоске), итог заменяется на {@code QR_REUSED}.
     *
     * <p>Anti-passback (FR-08): направление выбрано при скане, а между сканом и кадрами человек мог пройти на другом
     * киоске. Пропуск сверяется с присутствием под блокировкой: вход того, кто уже внутри, — {@code ALREADY_INSIDE},
     * выход того, кто уже вышел, — {@code NOT_INSIDE}. Присутствие при этом не меняется.</p>
     *
     * <p>Вместимость (FR-08, ACC-17): вход в зону с {@code capacity} сверяется со счётчиком «внутри» под блокировкой зоны;
     * мест нет — {@code ZONE_FULL}. Порядок блокировок: QR → присутствие → зона → журнал.</p>
     *
     * @param record   итог, посчитанный DecisionEngine
     * @param capacity вместимость зоны или {@code null} — без ограничения
     * @return записанный итог
     */
    @Transactional
    public PassRecord finish(PassRecord record, Integer capacity) {
        boolean consumed = qrUses.consume(record.deviceId(), record.window(), record.id(), record.ts());
        PassRecord result = consumed ? passback(record, capacity) : replaced(record, ReasonCode.QR_REUSED);
        write(result);
        return result;
    }

    /**
     * Фиксирует ручной проход охранника (FR-14): без QR, направление — по присутствию под блокировкой, так что
     * охранник не может «впустить» того, кто уже внутри: ручной проход изнутри — это выход.
     *
     * @param record ручной проход ({@code direction} перезаписывается)
     * @return записанный итог
     */
    @Transactional
    public PassRecord manual(PassRecord record) {
        Direction direction = presence.lockDirection(record.workerId(), record.ts(), props.presenceTtl());
        PassRecord result = new PassRecord(record.id(), record.ts(), record.checkpointId(), record.zoneId(), direction,
                record.workerId(), record.deviceId(), record.window(), record.faceScore(), record.livenessOk(), record.action(),
                record.decision(), record.code(), record.latencyMs(), record.source(), record.details());
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

    /**
     * Фиксирует историю симулятора (FR-34, ADR-007): журнал и {@code access.pass.v1} с {@code source = SIMULATOR}, как у
     * реальных проходов, но присутствие не трогается — симулятор не может «впустить» или «выпустить» живого человека.
     *
     * @param records попытки по времени, {@code source = SIMULATOR}
     * @throws IllegalArgumentException если среди них не симулятор
     */
    @Transactional
    public void simulated(List<PassRecord> records) {
        for (PassRecord rec : records) {
            if (rec.source() != AttemptSource.SIMULATOR) {
                throw new IllegalArgumentException("не симулятор: " + rec.id());
            }
            journal.append(rec);
            publish(rec);
        }
    }

    private void write(PassRecord rec) {
        journal.append(rec);
        if (rec.decision() == Decision.ALLOW || rec.decision() == Decision.MANUAL) {
            presence.apply(rec.workerId(), rec.zoneId(), rec.direction(), rec.ts());
        }
        publish(rec);
    }

    private void publish(PassRecord rec) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attemptId", rec.id().toString());
        payload.put("workerId", rec.workerId());
        payload.put("checkpointId", rec.checkpointId());
        payload.put("zoneId", rec.zoneId());
        payload.put("direction", rec.direction().name());
        payload.put("decision", rec.decision().name());
        payload.put("reasonCode", rec.code().name());
        payload.put("ts", rec.ts().toString());
        payload.put("source", rec.source().name());
        outbox.add(Topics.ACCESS_PASS_V1, "PassDecided", rec.workerId() == null ? rec.id().toString() : rec.workerId(), payload);
    }

    private PassRecord passback(PassRecord rec, Integer capacity) {
        if (rec.decision() != Decision.ALLOW) {
            return rec;
        }
        Direction actual = presence.lockDirection(rec.workerId(), rec.ts(), props.presenceTtl());
        if (actual != rec.direction()) {
            return replaced(rec, rec.direction() == Direction.IN ? ReasonCode.ALREADY_INSIDE : ReasonCode.NOT_INSIDE);
        }
        boolean full = rec.direction() == Direction.IN && capacity != null && rec.zoneId() != null
                && presence.zoneFull(rec.zoneId(), capacity, rec.workerId(), rec.ts(), props.presenceTtl());
        return full ? replaced(rec, ReasonCode.ZONE_FULL) : rec;
    }

    private static PassRecord replaced(PassRecord rec, ReasonCode code) {
        Map<String, Object> details = new LinkedHashMap<>(rec.details());
        details.put("computedCode", rec.code().name());
        return new PassRecord(rec.id(), rec.ts(), rec.checkpointId(), rec.zoneId(), rec.direction(), rec.workerId(),
                rec.deviceId(), rec.window(), rec.faceScore(), rec.livenessOk(), rec.action(), Decision.DENY, code,
                rec.latencyMs(), rec.source(), details);
    }
}
