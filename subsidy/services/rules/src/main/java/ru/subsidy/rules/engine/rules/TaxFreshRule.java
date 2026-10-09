package ru.subsidy.rules.engine.rules;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;

/**
 * DATE_TAX_FRESH: справка ФНС не старше taxMaxAgeDays.
 */
@Component
public class TaxFreshRule extends AbstractFreshnessRule {

    /**
     * Конструктор.
     */
    public TaxFreshRule() {
        super("DATE_TAX_FRESH", Docs.TAX, "certificateDate", "taxMaxAgeDays", "Справка ФНС");
    }
}
