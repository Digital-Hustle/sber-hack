package ru.documents.docproc.features.processing.gateway;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Ocr отказался обрабатывать файл (problem+json 4xx) — повтор не поможет.
 */
public class OcrRejectedException extends RuntimeException {

    private final transient JsonNode problem;

    /**
     * Отказ.
     *
     * @param problem тело ответа
     */
    public OcrRejectedException(JsonNode problem) {
        super(problem == null ? "OCR_REJECTED" : problem.path("code").asText("OCR_REJECTED"));
        this.problem = problem;
    }

    /**
     * Код ocr.
     *
     * @return код
     */
    public String code() {
        return problem == null ? "OCR_REJECTED" : problem.path("code").asText("OCR_REJECTED");
    }
}
