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
 * SUM_ESTIMATE_CONFIRMED: подтверждённые счетами (или договорами) затраты ≥ итога сметы.
 */
@Component
public class EstimateConfirmedRule implements Rule {

    private static final String CODE = "SUM_ESTIMATE_CONFIRMED";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> estimate = facts.first(Docs.ESTIMATE);
        Optional<BigDecimal> total = estimate.flatMap(doc -> doc.money(Docs.TOTAL));
        Optional<BigDecimal> confirmed = Sums.confirmed(facts);
        if (total.isEmpty() || confirmed.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет итога сметы или сумм в счетах/договорах"));
        }
        List<Evidence> where = List.of(Evidence.of(estimate.get(), Docs.TOTAL));
        return List.of(Money.greater(total.get(), confirmed.get())
                ? RuleResult.failed(CODE, "Подтверждено затрат " + Money.format(confirmed.get()) + " из "
                + Money.format(total.get()) + " по смете", where, Money.plain(total.get()),
                Money.plain(confirmed.get()))
                : RuleResult.passed(CODE, "Затраты сметы подтверждены: " + Money.format(confirmed.get()), where));
    }
}
