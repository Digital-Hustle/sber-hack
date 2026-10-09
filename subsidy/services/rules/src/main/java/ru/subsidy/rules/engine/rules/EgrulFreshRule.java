package ru.subsidy.rules.engine.rules;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;

/**
 * DATE_EGRUL_FRESH: выписка ЕГРЮЛ не старше egrulMaxAgeDays.
 */
@Component
public class EgrulFreshRule extends AbstractFreshnessRule {

    /**
     * Конструктор.
     */
    public EgrulFreshRule() {
        super("DATE_EGRUL_FRESH", Docs.EGRUL, "extractDate", "egrulMaxAgeDays", "Выписка ЕГРЮЛ");
    }
}
