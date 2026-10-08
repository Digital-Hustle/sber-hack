package ru.proxodnaya.access.api.controller.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.KioskController;
import ru.proxodnaya.access.api.dto.request.CreateAttemptRq;
import ru.proxodnaya.access.api.dto.request.SubmitFramesRq;
import ru.proxodnaya.access.api.dto.response.ChallengeRs;
import ru.proxodnaya.access.api.dto.response.CreateAttemptRs;
import ru.proxodnaya.access.api.dto.response.DecisionPreviewRs;
import ru.proxodnaya.access.api.dto.response.PassDecisionRs;
import ru.proxodnaya.access.features.pass.application.PassService;
import ru.proxodnaya.access.features.pass.application.PassService.AttemptStartResult;
import ru.proxodnaya.access.features.pass.model.DecisionResult;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.access.shared.ReasonView;

/**
 * Реализация API киоска: приём QR → челлендж → кадры → решение.
 */
@RestController
public class KioskControllerImpl implements KioskController {

    private final PassService passService;

    public KioskControllerImpl(PassService passService) {
        this.passService = passService;
    }

    @Override
    public ResponseEntity<CreateAttemptRs> createAttempt(CreateAttemptRq request) {
        Direction direction = Direction.valueOf(request.direction());
        AttemptStartResult result = passService.startAttempt(request.qr(), direction, request.checkpointId());

        if (result.challengeRequired()) {
            ChallengeRs challenge = new ChallengeRs(
                    result.challenge().action().name(),
                    result.challenge().textRu(),
                    result.challenge().framesRequired(),
                    result.challenge().expiresAt()
            );
            return ResponseEntity.ok(CreateAttemptRs.challenge(result.attemptId().toString(), challenge));
        }

        ReasonView reason = ReasonView.of(result.reasonCode());
        DecisionPreviewRs preview = new DecisionPreviewRs(
                Decision.DENY.name(),
                reason.code(),
                reason.message(),
                reason.hint()
        );
        return ResponseEntity.ok(CreateAttemptRs.deny(result.attemptId().toString(), preview));
    }

    @Override
    public ResponseEntity<PassDecisionRs> submitFrames(String attemptId, SubmitFramesRq request) {
        UUID id = UUID.fromString(attemptId);
        // В полной реализации: челлендж и QR хранятся в pending-таблице между шагами
        // Для сейчас — упрощённый путь: кадры передаются в PassService
        DecisionResult result = passService.submitFrames(
                id,
                null, // qr — из pending
                Direction.IN, // из pending
                "cp_main", // из pending
                null, // zoneId — из checkpoint
                request.frames(),
                "TURN_LEFT", // из pending
                Instant.now()
        );

        ReasonView reason = ReasonView.of(result.reasonCode());
        PassDecisionRs response = new PassDecisionRs(
                attemptId,
                result.decision().name(),
                reason.code(),
                reason.message(),
                reason.hint(),
                result.faceScore(),
                null // worker preview — из people
        );
        return ResponseEntity.ok(response);
    }
}
