package ru.proxodnaya.access.features.pass.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.features.pass.model.PendingAttempt;
import ru.proxodnaya.access.shared.ChallengeAction;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.jooq.Tables;

/**
 * Попытки, ждущие кадры.
 */
@Repository
public class ChallengeRepository {

    private static final ru.proxodnaya.access.jooq.tables.AttemptChallenges ATTEMPT_CHALLENGES = Tables.ATTEMPT_CHALLENGES;

    private final DSLContext dsl;

    public ChallengeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сохраняет попытку.
     *
     * @param attempt попытка
     */
    public void insert(PendingAttempt attempt) {
        dsl.insertInto(ATTEMPT_CHALLENGES)
                .set(ATTEMPT_CHALLENGES.TOKEN, attempt.token())
                .set(ATTEMPT_CHALLENGES.WORKER_ID, attempt.workerId())
                .set(ATTEMPT_CHALLENGES.DEVICE_ID, attempt.deviceId())
                .set(ATTEMPT_CHALLENGES.QR_WINDOW, attempt.window())
                .set(ATTEMPT_CHALLENGES.DIRECTION, attempt.direction().name())
                .set(ATTEMPT_CHALLENGES.CHECKPOINT_ID, attempt.checkpointId())
                .set(ATTEMPT_CHALLENGES.ZONE_ID, attempt.zoneId())
                .set(ATTEMPT_CHALLENGES.ACTION, attempt.action().name())
                .set(ATTEMPT_CHALLENGES.FRAMES_REQUIRED, attempt.framesRequired())
                .set(ATTEMPT_CHALLENGES.STARTED_AT, attempt.startedAt().atOffset(ZoneOffset.UTC))
                .set(ATTEMPT_CHALLENGES.EXPIRES_AT, attempt.expiresAt().atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Забирает попытку: удаляет и возвращает, повторно забрать нельзя.
     *
     * @param token токен
     * @return попытка или пусто
     */
    public Optional<PendingAttempt> take(UUID token) {
        return dsl.deleteFrom(ATTEMPT_CHALLENGES)
                .where(ATTEMPT_CHALLENGES.TOKEN.eq(token))
                .returning()
                .fetchOptional(row -> new PendingAttempt(row.getToken(), row.getWorkerId(), row.getDeviceId(), row.getQrWindow(),
                        Direction.valueOf(row.getDirection()), row.getCheckpointId(), row.getZoneId(),
                        ChallengeAction.valueOf(row.getAction()),
                        row.getFramesRequired(), row.getStartedAt().toInstant(), row.getExpiresAt().toInstant()));
    }

    /**
     * Удаляет брошенные попытки.
     *
     * @param before истекли раньше этого момента
     * @return сколько удалено
     */
    public int deleteExpired(Instant before) {
        return dsl.deleteFrom(ATTEMPT_CHALLENGES)
                .where(ATTEMPT_CHALLENGES.EXPIRES_AT.lt(before.atOffset(ZoneOffset.UTC)))
                .execute();
    }
}
