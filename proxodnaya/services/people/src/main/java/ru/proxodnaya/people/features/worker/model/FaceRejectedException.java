package ru.proxodnaya.people.features.worker.model;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Снимок не принят эталоном в face: {@code reason} — что исправить (NO_FACE, TOO_DARK, …). Ответ 422.
 */
public class FaceRejectedException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final String reason;

    /**
     * Отказ face.
     *
     * @param code    FACE_LOW_QUALITY или FACE_MULTIPLE
     * @param message текст для человека
     * @param reason  код качества снимка или {@code null}
     */
    public FaceRejectedException(ErrorCode code, String message, String reason) {
        super(code, message);
        this.reason = reason;
    }

    /**
     * Код качества снимка.
     *
     * @return код или {@code null}
     */
    public String reason() {
        return reason;
    }
}
