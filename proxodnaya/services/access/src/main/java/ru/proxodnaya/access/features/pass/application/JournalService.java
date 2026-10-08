package ru.proxodnaya.access.features.pass.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Журнал попыток прохода: append-only, hash-chain (FR-10, FR-67).
 * Запись в pass_attempts + триггер запрещает UPDATE/DELETE.
 * prev_hash = hash предыдущей записи; hash = SHA-256(prev_hash || record).
 */
@Service
public class JournalService {

    private static final String INSERT_SQL = """
            insert into pass_attempts (
                id, ts, checkpoint_id, direction, claimed_worker_id, device_id, qr_window,
                face_score, liveness_ok, challenge_action, decision, reason_code,
                details, latency_ms, source, prev_hash, hash
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            """;

    private static final String LAST_HASH_SQL = "select hash from pass_attempts order by ts desc, id desc limit 1";

    private static final String COUNT_SQL = """
            select count(*) from pass_attempts
            where claimed_worker_id = ? and ts > now() - interval '10 minutes'
            and decision = 'DENY' and reason_code in ('FACE_MISMATCH', 'LIVENESS_FAILED')
            """;

    private final JdbcTemplate jdbc;

    public JournalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Записывает попытку в журнал с hash-chain. Вызывается внутри транзакции PassService.
     *
     * @return id записанной попытки
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(
            UUID attemptId, Instant ts, String checkpointId, Direction direction,
            String workerId, String deviceId, long qrWindow,
            Double faceScore, boolean livenessOk, String challengeAction,
            Decision decision, ReasonCode reasonCode,
            long latencyMs, AttemptSource source
    ) {
        // prev_hash: последняя запись в журнале
        String prevHash = jdbc.query(LAST_HASH_SQL, rs -> rs.next() ? rs.getString(1) : "GENESIS");

        // hash = SHA-256(prev_hash || id|ts|checkpoint|direction|worker|device|window|score|liveness|challenge|decision|reason|latency|source)
        String record = "%s|%s|%s|%s|%s|%s|%d|%s|%s|%s|%s|%s|%d|%s".formatted(
                attemptId, ts, checkpointId, direction,
                workerId != null ? workerId : "", deviceId != null ? deviceId : "", qrWindow,
                faceScore != null ? faceScore.toString() : "",
                livenessOk, challengeAction != null ? challengeAction : "",
                decision, reasonCode, latencyMs, source);
        String hash = sha256(prevHash + "|" + record);

        jdbc.update(INSERT_SQL,
                attemptId, ts, checkpointId, direction.name(),
                workerId, deviceId, qrWindow,
                faceScore, livenessOk, challengeAction,
                decision.name(), reasonCode.name(),
                "{}", (int) latencyMs, source.name(),
                prevHash, hash
        );

        return attemptId;
    }

    /**
     * Считает недавние неудачные сверки лица (для TEMP_LOCKED).
     */
    public int countRecentFaceFailures(String workerId) {
        if (workerId == null) return 0;
        Integer count = jdbc.queryForObject(COUNT_SQL, Integer.class, workerId);
        return count != null ? count : 0;
    }

    /**
     * Проверяет целостность hash-chain журнала (FR-67).
     * @return ok=true если все хеши сходятся, brokenAt = id первой сломанной записи
     */
    public VerifyResult verifyIntegrity() {
        var rows = jdbc.query("""
                select id, prev_hash, hash, ts::text, checkpoint_id, direction, claimed_worker_id,
                       device_id, qr_window, face_score, liveness_ok, challenge_action,
                       decision, reason_code, latency_ms, source
                from pass_attempts order by ts asc, id asc
                """, (rs, rowNum) -> new AttemptRow(
                rs.getString("prev_hash"),
                rs.getString("hash"),
                rs.getString("id"), rs.getString("ts"),
                rs.getString("checkpoint_id"), rs.getString("direction"),
                rs.getString("claimed_worker_id"), rs.getString("device_id"),
                rs.getLong("qr_window"),
                rs.getString("face_score") != null ? rs.getString("face_score") : "",
                rs.getBoolean("liveness_ok"),
                rs.getString("challenge_action") != null ? rs.getString("challenge_action") : "",
                rs.getString("decision"), rs.getString("reason_code"),
                rs.getInt("latency_ms"), rs.getString("source")
        ));

        String expectedPrev = "GENESIS";
        for (AttemptRow row : rows) {
            if (!expectedPrev.equals(row.prevHash())) {
                return new VerifyResult(false, row.id());
            }
            String record = "%s|%s|%s|%s|%s|%s|%d|%s|%s|%s|%s|%s|%d|%s".formatted(
                    row.id(), row.ts(), row.checkpoint(), row.direction(),
                    row.worker() != null ? row.worker() : "",
                    row.deviceId() != null ? row.deviceId() : "", row.qrWindow(),
                    row.faceScore(), row.livenessOk(), row.challengeAction(),
                    row.decision(), row.reason(), row.latency(), row.source());
            String expectedHash = sha256(expectedPrev + "|" + record);
            if (!expectedHash.equals(row.hash())) {
                return new VerifyResult(false, row.id());
            }
            expectedPrev = row.hash();
        }
        return new VerifyResult(true, null);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record AttemptRow(String prevHash, String hash, String id, String ts,
                               String checkpoint, String direction, String worker,
                               String deviceId, long qrWindow, String faceScore,
                               boolean livenessOk, String challengeAction,
                               String decision, String reason, int latency, String source) {}

    public record VerifyResult(boolean ok, String brokenAt) {}
}
