package ru.documents.docproc.features.processing.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Результат шага: выход для stage_outputs и, если документ завершён, итог.
 *
 * @param output выход шага
 * @param result итог (null — идём дальше)
 */
public record StepOutcome(ObjectNode output, FinalResult result) {

    /**
     * Продолжить.
     *
     * @param output выход
     * @return результат
     */
    public static StepOutcome next(ObjectNode output) {
        return new StepOutcome(output, null);
    }
}
