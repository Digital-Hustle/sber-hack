package ru.documents.docproc.features.processing.rules;

import java.util.List;

import ru.documents.docproc.features.processing.model.FieldResult.Check;

/**
 * Уверенность поля (ADR-011): {@code min(1, w1·c_model + w2·c_ocr + w3·c_checks)}.
 */
public final class ConfidenceScorer {

    private final double wModel;
    private final double wOcr;
    private final double wChecks;
    private final double notApplicable;

    /**
     * Веса из настроек.
     *
     * @param wModel        вес модели
     * @param wOcr          вес OCR
     * @param wChecks       вес проверок
     * @param notApplicable оценка, если проверок нет
     */
    public ConfidenceScorer(double wModel, double wOcr, double wChecks, double notApplicable) {
        this.wModel = wModel;
        this.wOcr = wOcr;
        this.wChecks = wChecks;
        this.notApplicable = notApplicable;
    }

    /**
     * Оценка проверок: все пройдены — 1, нет проверок — notApplicable, есть провал — 0.
     *
     * @param checks проверки
     * @return 0..1
     */
    public double checksScore(List<Check> checks) {
        if (checks.isEmpty()) {
            return notApplicable;
        }
        return checks.stream().allMatch(Check::ok) ? 1.0 : 0.0;
    }

    /**
     * Уверенность.
     *
     * @param model  самооценка модели 0..1
     * @param ocr    уверенность распознавания источника 0..1
     * @param checks проверки
     * @return 0..1, 3 знака
     */
    public double score(double model, double ocr, List<Check> checks) {
        double c = wModel * clamp(model) + wOcr * clamp(ocr) + wChecks * checksScore(checks);
        return Math.round(Math.min(1.0, c) * 1000) / 1000.0;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
