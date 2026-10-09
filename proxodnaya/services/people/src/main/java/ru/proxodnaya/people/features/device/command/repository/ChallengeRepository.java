package ru.proxodnaya.people.features.device.command.repository;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.DeviceAuthChallenges;

/**
 * Одноразовые challenge для device-токена (PPL-05).
 */
@Repository
public class ChallengeRepository {

    private static final DeviceAuthChallenges CHALLENGES = Tables.DEVICE_AUTH_CHALLENGES;
    /** Сколько хранить погашенные и просроченные challenge — для разбора инцидентов хватает. */
    private static final Duration KEEP = Duration.ofHours(1);

    private final DSLContext dsl;

    public ChallengeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Сохраняет challenge и заодно чистит старые.
     *
     * @param challenge значение
     * @param deviceId  устройство
     * @param now       момент
     * @param expiresAt действует до
     */
    public void insert(String challenge, String deviceId, Instant now, Instant expiresAt) {
        dsl.deleteFrom(CHALLENGES).where(CHALLENGES.EXPIRES_AT.lt(now.minus(KEEP).atOffset(ZoneOffset.UTC))).execute();
        dsl.insertInto(CHALLENGES)
                .set(CHALLENGES.CHALLENGE, challenge)
                .set(CHALLENGES.DEVICE_ID, deviceId)
                .set(CHALLENGES.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
                .execute();
    }

    /**
     * Гасит challenge одним UPDATE: второй запрос с тем же значением получит {@code false} даже при гонке.
     *
     * @param challenge значение
     * @param deviceId  устройство, которому он выдан
     * @param now       момент
     * @return погашен ли (был выдан этому устройству, не использован и не истёк)
     */
    public boolean consume(String challenge, String deviceId, Instant now) {
        return dsl.update(CHALLENGES).set(CHALLENGES.USED_AT, now.atOffset(ZoneOffset.UTC))
                .where(CHALLENGES.CHALLENGE.eq(challenge)
                        .and(CHALLENGES.DEVICE_ID.eq(deviceId))
                        .and(CHALLENGES.USED_AT.isNull())
                        .and(CHALLENGES.EXPIRES_AT.gt(now.atOffset(ZoneOffset.UTC))))
                .execute() == 1;
    }
}
