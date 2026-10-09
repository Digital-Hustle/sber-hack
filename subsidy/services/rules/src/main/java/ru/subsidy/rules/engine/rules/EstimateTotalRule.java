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
 * SUM_ESTIMATE_TOTAL: Σ статей сметы = итог сметы.
 */
@Component
public class EstimateTotalRule implements Rule {

    private static final String CODE = "SUM_ESTIMATE_TOTAL";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> estimate = facts.first(Docs.ESTIMATE);
        Optional<BigDecimal> total = estimate.flatMap(doc -> doc.money(Docs.TOTAL));
        Optional<BigDecimal> sum = estimate.flatMap(Sums::items);
        if (total.isEmpty() || sum.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет статей или итога в смете"));
        }
        List<Evidence> where = List.of(Evidence.of(estimate.get(), Docs.ITEMS), Evidence.of(estimate.get(), Docs.TOTAL));
        return List.of(Money.same(sum.get(), total.get())
                ? RuleResult.passed(CODE, "Сумма статей сметы = итог " + Money.format(total.get()), where)
                : RuleResult.failed(CODE, "Сумма статей сметы " + Money.format(sum.get()) + " ≠ итог "
                + Money.format(total.get()), where, Money.plain(total.get()), Money.plain(sum.get())));
    }
}
