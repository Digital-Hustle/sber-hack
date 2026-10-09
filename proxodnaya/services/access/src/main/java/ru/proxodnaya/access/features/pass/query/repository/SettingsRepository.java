package ru.proxodnaya.access.features.pass.query.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.proxodnaya.access.api.dto.response.SettingsRs;
import ru.proxodnaya.access.jooq.Tables;

@Repository
public class SettingsRepository {

    private final DSLContext dsl;
    public SettingsRepository(DSLContext dsl) { this.dsl = dsl; }

    public SettingsRs find() {
        var r = dsl.selectFrom(Tables.ACCESS_SETTINGS).fetchOne();
        if (r == null) return defaultSettings();
        return SettingsRs.builder()
                .faceThreshold(r.getFaceThreshold()).qrToleranceSec(r.getQrToleranceSec())
                .shiftGraceMin(r.getShiftGraceMin()).requireShift(r.getRequireShift())
                .tempLockFailures(r.getTempLockFailures()).tempLockWindowSec(r.getTempLockWindowSec())
                .framesRequired(r.getFramesRequired()).challengeTtlSec(r.getChallengeTtlSec())
                .repeatScanCooldownSec(r.getRepeatScanCooldownSec()).build();
    }

    public SettingsRs update(SettingsRs s) {
        dsl.update(Tables.ACCESS_SETTINGS)
                .set(Tables.ACCESS_SETTINGS.FACE_THRESHOLD, s.getFaceThreshold())
                .set(Tables.ACCESS_SETTINGS.QR_TOLERANCE_SEC, s.getQrToleranceSec())
                .set(Tables.ACCESS_SETTINGS.SHIFT_GRACE_MIN, s.getShiftGraceMin())
                .set(Tables.ACCESS_SETTINGS.REQUIRE_SHIFT, s.isRequireShift())
                .set(Tables.ACCESS_SETTINGS.TEMP_LOCK_FAILURES, s.getTempLockFailures())
                .set(Tables.ACCESS_SETTINGS.TEMP_LOCK_WINDOW_SEC, s.getTempLockWindowSec())
                .set(Tables.ACCESS_SETTINGS.FRAMES_REQUIRED, s.getFramesRequired())
                .set(Tables.ACCESS_SETTINGS.CHALLENGE_TTL_SEC, s.getChallengeTtlSec())
                .set(Tables.ACCESS_SETTINGS.REPEAT_SCAN_COOLDOWN_SEC, s.getRepeatScanCooldownSec())
                .set(Tables.ACCESS_SETTINGS.UPDATED_AT, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))
                .execute();
        return s;
    }

    private static SettingsRs defaultSettings() {
        return SettingsRs.builder().faceThreshold(0.6).qrToleranceSec(90).shiftGraceMin(60)
                .requireShift(true).tempLockFailures(5).tempLockWindowSec(600)
                .framesRequired(3).challengeTtlSec(10).repeatScanCooldownSec(30).build();
    }
}