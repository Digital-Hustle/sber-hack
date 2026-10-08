package ru.proxodnaya.common.exception;

/**
 * Коды ошибок API (см. docs/API.md). Используются в ProblemDetail.code.
 */
public enum ErrorCode {

    // QR / device
    QR_MISSING,
    QR_INVALID,
    QR_EXPIRED,
    QR_REUSED,
    DEVICE_UNKNOWN,
    DEVICE_REVOKED,
    DEVICE_MISMATCH,
    KIOSK_SIGNATURE_INVALID,

    // Worker
    WORKER_BLOCKED,
    TEMP_LOCKED,

    // Face
    FACE_NOT_FOUND,
    FACE_MULTIPLE,
    FACE_LOW_QUALITY,
    FACE_MISMATCH,
    LIVENESS_FAILED,
    CHALLENGE_EXPIRED,

    // Shift / permit
    NO_SHIFT,
    OUTSIDE_SHIFT_WINDOW,
    PERMIT_EXPIRED,
    NO_ZONE_PERMIT,

    // Anti-passback
    ALREADY_INSIDE,
    NOT_INSIDE,
    ZONE_FULL,

    // People
    WORKER_NOT_FOUND,
    INVITE_INVALID,
    INVITE_EXPIRED,
    DEVICE_ALREADY_BOUND,
    CHALLENGE_INVALID,
    PAIRING_CODE_INVALID,

    // System
    SYSTEM_ERROR,

    // Generic HTTP
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN,
    VALIDATION_ERROR;

    public String code() {
        return name();
    }
}
