package ru.documents.common.event;

/**
 * Топики Kafka: владелец.агрегат.vN (docs/ARCHITECTURE.md «События»).
 */
public final class Topics {

    public static final String INTAKE_APPLICATION_V1 = "intake.application.v1";
    public static final String INTAKE_DOCUMENT_V1 = "intake.document.v1";
    public static final String DOCPROC_DOCUMENT_V1 = "docproc.document.v1";
    public static final String REVIEW_APPLICATION_V1 = "review.application.v1";

    private Topics() {
    }
}
