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
    KIOSK_UNKNOWN,
    KIOSK_NOT_PAIRED,
    CODE_WEAK,

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

    // Staff (вход по коду из письма, ADR-041)
    LOGIN_RESEND_TOO_SOON,
    LOGIN_CODE_INVALID,
    LOGIN_CODE_EXPIRED,
    LOGIN_CODE_LOCKED,
    SESSION_EXPIRED,
    LAST_ADMIN,

    // System
    SYSTEM_ERROR,
    SERVICE_UNAVAILABLE,

    // Generic HTTP
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN,
    VALIDATION_ERROR;

    public String code() {
        return name();
    }
}
