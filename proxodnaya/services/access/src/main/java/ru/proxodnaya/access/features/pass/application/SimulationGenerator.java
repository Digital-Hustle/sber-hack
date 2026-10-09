package ru.proxodnaya.access.features.pass.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Правдоподобный рабочий день: по будням вход около 08:00 (каждый десятый — с опозданием), выход около 17:00,
 * иногда отказ перед входом (лицо, QR, качество кадра). Чистая функция от зерна — тестируется без БД.
 *
 * @param zone площадка
 */
public record SimulationGenerator(ZoneId zone) {

    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(17, 0);
    private static final int EARLY_MINUTES = 20;
    private static final int LATE_MINUTES = 40;
    private static final int OVERTIME_MINUTES = 60;
    private static final int LATE_EVERY = 10;
    private static final int RETRY_SECONDS = 90;
    private static final double SCORE_BASE = 0.55;
    private static final double SCORE_SPREAD = 0.35;
    private static final List<ReasonCode> DENY_REASONS = List.of(ReasonCode.FACE_MISMATCH, ReasonCode.QR_EXPIRED,
            ReasonCode.FACE_LOW_QUALITY, ReasonCode.LIVENESS_FAILED, ReasonCode.FACE_NOT_FOUND);
    private static final Map<String, Object> DETAILS = Map.of("simulated", true);

    /**
     * Попытки прохода за прошедшие будни.
     *
     * @param checkpointId проходная
     * @param zoneId       её зона
     * @param workerIds    рабочие
     * @param days         сколько дней назад от {@code today} (не включая его)
     * @param denyRate     доля дней с отказом перед входом
     * @param today        сегодня на площадке
     * @param random       источник случайности
     * @return попытки по времени
     */
    public List<PassRecord> generate(String checkpointId, String zoneId, List<String> workerIds, int days, double denyRate,
                                     LocalDate today, Random random) {
        List<PassRecord> records = new ArrayList<>();
        for (int back = days; back >= 1; back--) {
            LocalDate day = today.minusDays(back);
            if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            for (String workerId : workerIds) {
                Instant entry = at(day, START, random.nextInt(LATE_EVERY) == 0
                        ? random.nextInt(LATE_MINUTES) + 1 : -random.nextInt(EARLY_MINUTES));
                if (random.nextDouble() < denyRate) {
                    ReasonCode reason = DENY_REASONS.get(random.nextInt(DENY_REASONS.size()));
                    records.add(record(checkpointId, zoneId, workerId, entry.minusSeconds(RETRY_SECONDS), Direction.IN, Decision.DENY,
                            reason, random));
                }
                records.add(record(checkpointId, zoneId, workerId, entry, Direction.IN, Decision.ALLOW, ReasonCode.OK, random));
                Instant out = at(day, END, random.nextInt(OVERTIME_MINUTES));
                records.add(record(checkpointId, zoneId, workerId, out, Direction.OUT, Decision.ALLOW, ReasonCode.OK, random));
            }
        }
        records.sort((left, right) -> left.ts().compareTo(right.ts()));
        return records;
    }

    private Instant at(LocalDate day, LocalTime time, int minutes) {
        return day.atTime(time).plusMinutes(minutes).atZone(zone).toInstant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static PassRecord record(String checkpointId, String zoneId, String workerId, Instant moment, Direction direction,
                                     Decision decision, ReasonCode code, Random random) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        double score = decision == Decision.ALLOW ? SCORE_BASE + random.nextDouble() * SCORE_SPREAD : random.nextDouble() * SCORE_BASE;
        return new PassRecord(id, moment, checkpointId, zoneId, direction, workerId, null, null, score, decision == Decision.ALLOW,
                null, decision, code, 0, AttemptSource.SIMULATOR, DETAILS);
    }
}
