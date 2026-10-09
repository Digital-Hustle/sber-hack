package ru.documents.docproc.features.processing.model;

import java.util.Set;

/**
 * Стадии документа (docs/API.md «Стадии документа»).
 */
public final class Stages {

    public static final String QUEUED = "QUEUED";
    public static final String EXTRACTING_TEXT = "EXTRACTING_TEXT";
    public static final String CLASSIFYING = "CLASSIFYING";
    public static final String EXTRACTING_FIELDS = "EXTRACTING_FIELDS";
    public static final String VALIDATING = "VALIDATING";
    public static final String PARSED = "PARSED";
    public static final String NEEDS_REVIEW = "NEEDS_REVIEW";
    public static final String UNREADABLE = "UNREADABLE";
    public static final String WAITING_MODEL = "WAITING_MODEL";
    public static final String FAILED = "FAILED";
    public static final String UNKNOWN = "UNKNOWN";

    public static final Set<String> FINAL = Set.of(PARSED, NEEDS_REVIEW, UNREADABLE, FAILED);

    private Stages() {
    }

    /**
     * Следующий шаг конвейера.
     *
     * @param step шаг
     * @return следующий
     */
    public static String next(String step) {
        return switch (step) {
            case EXTRACTING_TEXT -> CLASSIFYING;
            case CLASSIFYING -> EXTRACTING_FIELDS;
            case EXTRACTING_FIELDS -> VALIDATING;
            default -> throw new IllegalArgumentException(step);
        };
    }
}
