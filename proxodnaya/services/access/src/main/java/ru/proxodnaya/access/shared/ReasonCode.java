package ru.proxodnaya.access.shared;

/**
 * Коды причин отказа/успеха прохода (см. docs/API.md «Коды отказов прохода»).
 * Порядок проверок в DecisionEngine: первая проваленная проверка — и есть причина.
 */
public enum ReasonCode {
    OK,
    QR_MISSING,
    QR_INVALID,
    QR_EXPIRED,
    QR_REUSED,
    DEVICE_UNKNOWN,
    DEVICE_REVOKED,
    DEVICE_MISMATCH,
    WORKER_BLOCKED,
    FACE_NOT_FOUND,
    FACE_MULTIPLE,
    FACE_LOW_QUALITY,
    FACE_MISMATCH,
    LIVENESS_FAILED,
    CHALLENGE_EXPIRED,
    NO_SHIFT,
    OUTSIDE_SHIFT_WINDOW,
    PERMIT_EXPIRED,
    NO_ZONE_PERMIT,
    ALREADY_INSIDE,
    NOT_INSIDE,
    ZONE_FULL,
    TEMP_LOCKED,
    KIOSK_SIGNATURE_INVALID,
    SYSTEM_ERROR;

    public boolean isOk() {
        return this == OK;
    }

    public boolean isSystem() {
        return switch (this) {
            case FACE_NOT_FOUND, FACE_LOW_QUALITY, CHALLENGE_EXPIRED, SYSTEM_ERROR, QR_MISSING -> true;
            default -> false;
        };
    }
}
