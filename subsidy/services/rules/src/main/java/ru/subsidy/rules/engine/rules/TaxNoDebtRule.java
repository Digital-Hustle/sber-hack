package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.money.Money;
import ru.subsidy.rules.program.ProgramParams;

/**
 * TAX_NO_DEBT: по справке ФНС нет задолженности.
 */
@Component
public class TaxNoDebtRule implements Rule {

    private static final String CODE = "TAX_NO_DEBT";
    private static final String HAS_DEBT = "hasDebt";
    private static final String DEBT = "debtAmount";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> tax = facts.first(Docs.TAX);
        Optional<BigDecimal> debt = tax.flatMap(doc -> doc.money(DEBT));
        Optional<Boolean> hasDebt = tax.flatMap(doc -> doc.bool(HAS_DEBT))
                .or(() -> debt.map(amount -> amount.signum() > 0));
        if (hasDebt.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "В справке ФНС не найден признак задолженности"));
        }
        List<Evidence> where = List.of(Evidence.of(tax.orElseThrow(), HAS_DEBT));
        return List.of(hasDebt.get()
                ? RuleResult.failed(CODE, "По справке ФНС есть задолженность" + debt.map(amount -> ": "
                + Money.format(amount)).orElse(""), where, "нет задолженности", debt.map(Money::plain)
                .orElse("есть"))
                : RuleResult.passed(CODE, "Задолженности по налогам нет", where));
    }
}
