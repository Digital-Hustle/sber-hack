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
        return new SettingsRs(
                r.getFaceThreshold(), r.getQrToleranceSec(), r.getShiftGraceMin(),
                r.getRequireShift(), r.getTempLockFailures(), r.getTempLockWindowSec(),
                r.getFramesRequired(), r.getChallengeTtlSec(), r.getRepeatScanCooldownSec()
        );
    }

    public SettingsRs update(SettingsRs s) {
        dsl.update(Tables.ACCESS_SETTINGS)
                .set(Tables.ACCESS_SETTINGS.FACE_THRESHOLD, s.faceThreshold())
                .set(Tables.ACCESS_SETTINGS.QR_TOLERANCE_SEC, s.qrToleranceSec())
                .set(Tables.ACCESS_SETTINGS.SHIFT_GRACE_MIN, s.shiftGraceMin())
                .set(Tables.ACCESS_SETTINGS.REQUIRE_SHIFT, s.requireShift())
                .set(Tables.ACCESS_SETTINGS.TEMP_LOCK_FAILURES, s.tempLockFailures())
                .set(Tables.ACCESS_SETTINGS.TEMP_LOCK_WINDOW_SEC, s.tempLockWindowSec())
                .set(Tables.ACCESS_SETTINGS.FRAMES_REQUIRED, s.framesRequired())
                .set(Tables.ACCESS_SETTINGS.CHALLENGE_TTL_SEC, s.challengeTtlSec())
                .set(Tables.ACCESS_SETTINGS.REPEAT_SCAN_COOLDOWN_SEC, s.repeatScanCooldownSec())
                .set(Tables.ACCESS_SETTINGS.UPDATED_AT, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))
                .execute();
        return s;
    }

    private static SettingsRs defaultSettings() {
        return new SettingsRs(0.6, 90, 60, true, 5, 600, 3, 10, 30);
    }
}