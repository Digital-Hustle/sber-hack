package ru.proxodnaya.access.features.pass.application;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ru.proxodnaya.access.features.pass.model.DecisionResult;
import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.features.pass.model.PassContext;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Движок решений о проходе (FR-01). Пошаговая проверка по порядку из ARCHITECTURE.md:
 * fail-fast — первая проваленная проверка и есть причина отказа.
 *
 * <p>Порядок проверок:</p>
 * <ol>
 *   <li>QR: формат → устройство → подпись → окно → гашение</li>
 *   <li>Рабочий существует и не заблокирован</li>
 *   <li>Лицо: качество → одно лицо → совпадение с эталоном</li>
 *   <li>Живость</li>
 *   <li>Смена: есть смена, время в окне</li>
 *   <li>Допуск: инструктаж/медосмотр не просрочен, допуск в зону</li>
 *   <li>Anti-passback: вход только снаружи, выход только изнутри</li>
 *   <li>Вместимость зоны (опционально)</li>
 * </ol>
 *
 * <p><b>Красная линия (rules.md §1.1):</b> решение принимается только здесь на сервере.
 * Клиент получает готовый ALLOW/DENY.</p>
 */
@Service
public class DecisionEngine {

    private static final int MAX_RECENT_FAILURES = 5; // TEMP_LOCKED порог
    private static final int MIN_FRAMES_QUALITY = 0; // минимальное качество (face определяет)

    /**
     * Принимает решение по контексту. Возвращает DecisionResult с кодом причины.
     *
     * @param ctx контекст со всеми собранными данными
     * @return ALLOW / DENY / ERROR + ReasonCode
     */
    public DecisionResult decide(PassContext ctx) {
        UUID attemptId = ctx.attemptId();

        // 0. TEMP_LOCKED: слишком много недавних неудач
        if (ctx.recentFailures() >= MAX_RECENT_FAILURES) {
            return DecisionResult.deny(attemptId, ReasonCode.TEMP_LOCKED, ctx.workerId(), null);
        }

        // 1. QR: формат уже проверен в QrParser; устройство → подпись → окно проверены в QrVerifier.
        //    Здесь проверяем только что QR есть (парсер отдал null → QR_INVALID уже обработан в сервисе).

        // 2. Рабочий заблокирован
        if ("BLOCKED".equals(ctx.workerStatus())) {
            return DecisionResult.deny(attemptId, ReasonCode.WORKER_BLOCKED, ctx.workerId(), null);
        }

        // 3. Лицо: качество → совпадение → живость
        FaceResult face = ctx.faceResult();
        if (face != null) {
            if (!face.liveness()) {
                return DecisionResult.deny(attemptId, ReasonCode.LIVENESS_FAILED, ctx.workerId(), face.score());
            }
            if (face.quality() < 0.3) {
                return DecisionResult.deny(attemptId, ReasonCode.FACE_LOW_QUALITY, ctx.workerId(), face.score());
            }
            if (!face.match()) {
                return DecisionResult.deny(attemptId, ReasonCode.FACE_MISMATCH, ctx.workerId(), face.score());
            }
        }

        // 4. Смена
        if (!ctx.shiftActive()) {
            return DecisionResult.deny(attemptId, ReasonCode.NO_SHIFT, ctx.workerId(), null);
        }
        if (!ctx.shiftWindowOk()) {
            return DecisionResult.deny(attemptId, ReasonCode.OUTSIDE_SHIFT_WINDOW, ctx.workerId(), null);
        }

        // 5. Допуск
        if (ctx.permitUntil() != null && ctx.permitUntil().isBefore(LocalDate.ofInstant(ctx.now(), java.time.ZoneOffset.UTC))) {
            return DecisionResult.deny(attemptId, ReasonCode.PERMIT_EXPIRED, ctx.workerId(), null);
        }
        if (ctx.zoneId() != null && !ctx.workerZoneIds().contains(ctx.zoneId())) {
            return DecisionResult.deny(attemptId, ReasonCode.NO_ZONE_PERMIT, ctx.workerId(), null);
        }

        // 6. Anti-passback
        if (ctx.direction() == Direction.IN && ctx.isInside()) {
            return DecisionResult.deny(attemptId, ReasonCode.ALREADY_INSIDE, ctx.workerId(), null);
        }
        if (ctx.direction() == Direction.OUT && !ctx.isInside()) {
            return DecisionResult.deny(attemptId, ReasonCode.NOT_INSIDE, ctx.workerId(), null);
        }

        // 7. Вместимость зоны (опционально, только для входа)
        if (ctx.direction() == Direction.IN && ctx.zoneCapacity() > 0
                && ctx.zoneOccupancy() >= ctx.zoneCapacity()) {
            return DecisionResult.deny(attemptId, ReasonCode.ZONE_FULL, ctx.workerId(), null);
        }

        // 8. Всё ОК — ALLOW
        Double score = face != null ? face.score() : null;
        return DecisionResult.allow(attemptId, ctx.workerId(), score != null ? score : 0.0);
    }
}
