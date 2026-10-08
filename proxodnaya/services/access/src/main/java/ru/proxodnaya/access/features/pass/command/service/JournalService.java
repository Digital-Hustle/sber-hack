package ru.proxodnaya.access.features.pass.command.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.pass.command.repository.JournalRepository;
import ru.proxodnaya.access.features.pass.model.JournalCheck;
import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.jooq.tables.records.PassAttemptsRecord;

/**
 * Журнал попыток с hash-chain (FR-10, FR-67): {@code hash = SHA-256(prev_hash + "|" + canonical)}, первое звено от {@code "genesis"}.
 *
 * <p>canonical = id|ts|checkpoint|direction|worker|device|window|score|liveness|challenge|decision|reason|latency|source,
 * пустое поле — пустая строка, ts — ISO-8601 UTC с микросекундами. {@code details} в hash не входит.</p>
 */
@Service
public class JournalService {

    public static final String GENESIS = "genesis";
    private static final String SEP = "|";

    private final JournalRepository repository;

    public JournalService(JournalRepository repository) {
        this.repository = repository;
    }

    /**
     * Проверяет всю цепочку.
     *
     * @return результат проверки
     */
    @Transactional(readOnly = true)
    public JournalCheck verify() {
        AtomicReference<String> expectedPrev = new AtomicReference<>(GENESIS);
        AtomicReference<PassAttemptsRecord> broken = new AtomicReference<>();
        AtomicLong checked = new AtomicLong();
        repository.forEachInOrder(row -> {
            if (broken.get() == null) {
                String expected = sha256(expectedPrev.get() + SEP + canonical(row));
                if (expectedPrev.get().equals(row.getPrevHash()) && expected.equals(row.getHash())) {
                    checked.incrementAndGet();
                    expectedPrev.set(row.getHash());
                } else {
                    broken.set(row);
                }
            }
        });
        PassAttemptsRecord bad = broken.get();
        return new JournalCheck(bad == null, checked.get(), bad == null ? null : bad.getId());
    }

    /**
     * Добавляет звено. Только внутри транзакции решения.
     *
     * @param record запись
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(PassRecord record) {
        repository.lockChain();
        String prev = Objects.requireNonNullElse(repository.lastHash(), GENESIS);
        repository.insert(record, prev, sha256(prev + SEP + canonical(record)));
    }

    static String canonical(PassRecord rec) {
        return String.join(SEP, rec.id().toString(), rec.ts().toString(), rec.checkpointId(), rec.direction().name(),
                str(rec.workerId()), str(rec.deviceId()), str(rec.window()), str(rec.faceScore()), str(rec.livenessOk()),
                rec.action() == null ? "" : rec.action().name(), rec.decision().name(), rec.code().name(),
                Long.toString(rec.latencyMs()), rec.source().name());
    }

    private static String canonical(PassAttemptsRecord rec) {
        return String.join(SEP, rec.getId().toString(), rec.getTs().toInstant().toString(), rec.getCheckpointId(), rec.getDirection(),
                str(rec.getClaimedWorkerId()), str(rec.getDeviceId()), str(rec.getQrWindow()), str(rec.getFaceScore()),
                str(rec.getLivenessOk()), str(rec.getChallengeAction()), rec.getDecision(), rec.getReasonCode(),
                str(rec.getLatencyMs()), rec.getSource());
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 недоступен", error);
        }
    }
}
