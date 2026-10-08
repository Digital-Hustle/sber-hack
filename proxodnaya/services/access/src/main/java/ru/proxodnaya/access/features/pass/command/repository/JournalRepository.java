package ru.proxodnaya.access.features.pass.command.repository;

import java.time.ZoneOffset;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.jooq.tables.records.PassAttemptsRecord;
import ru.proxodnaya.access.jooq.Tables;

/**
 * Журнал попыток: только INSERT (UPDATE/DELETE запрещены триггером).
 */
@Repository
public class JournalRepository {

    private static final ru.proxodnaya.access.jooq.tables.PassAttempts PASS_ATTEMPTS = Tables.PASS_ATTEMPTS;

    /** Ключ advisory-lock цепочки: звенья добавляются строго по одному. */
    private static final long CHAIN_LOCK = 0x70617373L;
    private static final int FETCH_SIZE = 500;

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public JournalRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    /**
     * Берёт блокировку цепочки до конца транзакции. Без неё параллельные проходы разветвят hash-chain.
     */
    public void lockChain() {
        dsl.fetch("select pg_advisory_xact_lock(?)", CHAIN_LOCK);
    }

    /**
     * Hash последнего звена.
     *
     * @return hash или пусто для пустого журнала
     */
    public String lastHash() {
        return dsl.select(PASS_ATTEMPTS.HASH)
                .from(PASS_ATTEMPTS)
                .orderBy(PASS_ATTEMPTS.SEQ.desc())
                .limit(1)
                .fetchOne(PASS_ATTEMPTS.HASH);
    }

    /**
     * Добавляет звено.
     *
     * @param rec        запись
     * @param prevHash hash предыдущего звена
     * @param hash     hash этого звена
     */
    public void insert(PassRecord rec, String prevHash, String hash) {
        dsl.insertInto(PASS_ATTEMPTS)
                .set(PASS_ATTEMPTS.ID, rec.id())
                .set(PASS_ATTEMPTS.TS, rec.ts().atOffset(ZoneOffset.UTC))
                .set(PASS_ATTEMPTS.CHECKPOINT_ID, rec.checkpointId())
                .set(PASS_ATTEMPTS.DIRECTION, rec.direction().name())
                .set(PASS_ATTEMPTS.CLAIMED_WORKER_ID, rec.workerId())
                .set(PASS_ATTEMPTS.DEVICE_ID, rec.deviceId())
                .set(PASS_ATTEMPTS.QR_WINDOW, rec.window())
                .set(PASS_ATTEMPTS.FACE_SCORE, rec.faceScore())
                .set(PASS_ATTEMPTS.LIVENESS_OK, rec.livenessOk())
                .set(PASS_ATTEMPTS.CHALLENGE_ACTION, rec.action() == null ? null : rec.action().name())
                .set(PASS_ATTEMPTS.DECISION, rec.decision().name())
                .set(PASS_ATTEMPTS.REASON_CODE, rec.code().name())
                .set(PASS_ATTEMPTS.DETAILS, json(rec))
                .set(PASS_ATTEMPTS.LATENCY_MS, Math.toIntExact(rec.latencyMs()))
                .set(PASS_ATTEMPTS.SOURCE, rec.source().name())
                .set(PASS_ATTEMPTS.PREV_HASH, prevHash)
                .set(PASS_ATTEMPTS.HASH, hash)
                .execute();
    }

    /**
     * Проходит журнал по порядку звеньев.
     *
     * @param consumer обработчик строки
     */
    public void forEachInOrder(Consumer<PassAttemptsRecord> consumer) {
        try (Stream<PassAttemptsRecord> rows = dsl.selectFrom(PASS_ATTEMPTS)
                .orderBy(PASS_ATTEMPTS.SEQ)
                .fetchSize(FETCH_SIZE)
                .fetchStream()) {
            rows.forEach(consumer);
        }
    }

    private JSONB json(PassRecord rec) {
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(rec.details()));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("details попытки не сериализуются", error);
        }
    }
}
