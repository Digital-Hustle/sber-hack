package ru.proxodnaya.access.features.pass.application;

import java.util.Map;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.features.pass.model.DecisionResult;
import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.features.pass.model.PassContext;
import ru.proxodnaya.access.features.pass.model.WorkerProfile;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Решение о проходе на шаге кадров (FR-01, FR-07). Fail-fast и fail-closed: нет данных — отказ.
 *
 * <p>Порядок: зависимость упала → рабочий → устройство → лицо (найдено, одно, эталон есть, качество, совпадение, живость)
 * → для входа: смена, окно смены, допуск, зона. Выход не требует смены и допуска: человека не запираем на объекте.</p>
 */
@Component
public class DecisionEngine {

    static final double MIN_QUALITY = 0.3;

    private static final Map<String, ReasonCode> FACE_REASONS = Map.of(
            "FACE_NOT_FOUND", ReasonCode.FACE_NOT_FOUND,
            "FACE_MULTIPLE", ReasonCode.FACE_MULTIPLE,
            "FACE_LOW_QUALITY", ReasonCode.FACE_LOW_QUALITY,
            "FACE_NOT_ENROLLED", ReasonCode.FACE_NOT_ENROLLED,
            "NOT_ENROLLED", ReasonCode.FACE_NOT_ENROLLED,
            "LIVENESS_FAILED", ReasonCode.LIVENESS_FAILED);

    /**
     * Принимает решение.
     *
     * @param ctx собранные данные
     * @return ALLOW или DENY с причиной
     */
    public DecisionResult decide(PassContext ctx) {
        WorkerProfile profile = ctx.profile();
        FaceResult face = ctx.face();
        ReasonCode code;
        if (ctx.failedDependency() != null || face == null) {
            code = ReasonCode.SYSTEM_ERROR;
        } else if (profile == null || !profile.isActive()) {
            code = ReasonCode.WORKER_BLOCKED;
        } else if (!profile.deviceIds().contains(ctx.deviceId())) {
            code = ReasonCode.DEVICE_MISMATCH;
        } else {
            code = faceCode(face);
            if (code == ReasonCode.OK && ctx.direction() == Direction.IN) {
                code = entryCode(ctx);
            }
            if (code == ReasonCode.OK) {
                code = passbackCode(ctx);
            }
        }
        Double score = face == null ? null : face.score();
        return code == ReasonCode.OK ? DecisionResult.allow(score) : DecisionResult.deny(code, score);
    }

    private static ReasonCode faceCode(FaceResult face) {
        ReasonCode code = ReasonCode.OK;
        if (face.reason() != null && FACE_REASONS.containsKey(face.reason())) {
            code = FACE_REASONS.get(face.reason());
        } else if (face.quality() < MIN_QUALITY) {
            code = ReasonCode.FACE_LOW_QUALITY;
        } else if (!face.match()) {
            code = ReasonCode.FACE_MISMATCH;
        } else if (!face.liveness()) {
            code = ReasonCode.LIVENESS_FAILED;
        }
        return code;
    }

    private static ReasonCode entryCode(PassContext ctx) {
        WorkerProfile profile = ctx.profile();
        ReasonCode code = ReasonCode.OK;
        if (ctx.shift() == null) {
            code = ReasonCode.NO_SHIFT;
        } else if (!ctx.shift().windowOk()) {
            code = ReasonCode.OUTSIDE_SHIFT_WINDOW;
        } else if (profile.permitUntil() == null || profile.permitUntil().isBefore(ctx.today())) {
            code = ReasonCode.PERMIT_EXPIRED;
        } else if (ctx.zoneId() != null && !profile.zoneIds().contains(ctx.zoneId())) {
            code = ReasonCode.NO_ZONE_PERMIT;
        }
        return code;
    }

    /**
     * Anti-passback (FR-08): вход разрешён только снаружи, выход — только изнутри.
     *
     * @param ctx контекст
     * @return OK или ALREADY_INSIDE / NOT_INSIDE
     */
    private static ReasonCode passbackCode(PassContext ctx) {
        if (ctx.direction() == Direction.IN && ctx.inside()) {
            return ReasonCode.ALREADY_INSIDE;
        }
        if (ctx.direction() == Direction.OUT && !ctx.inside()) {
            return ReasonCode.NOT_INSIDE;
        }
        return ReasonCode.OK;
    }
}
