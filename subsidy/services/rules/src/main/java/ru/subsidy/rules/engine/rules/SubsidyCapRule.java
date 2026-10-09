package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 * SUM_SUBSIDY_CAP: запрошено ≤ min(rate × затраты, maxAmount). Затраты — итог сметы, но не больше подтверждённых.
 */
@Component
public class SubsidyCapRule implements Rule {

    private static final String REQUESTED_TEXT = "Запрошено ";
    private static final String CODE = "SUM_SUBSIDY_CAP";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<BigDecimal> rate = params.decimal("rate");
        Optional<BigDecimal> cap = params.money("maxAmount");
        Optional<DocFacts> app = facts.first(Docs.APPLICATION).filter(doc -> doc.money(Docs.REQUESTED).isPresent());
        Optional<DocFacts> estimate = facts.first(Docs.ESTIMATE);
        Optional<BigDecimal> requested = app.flatMap(doc -> doc.money(Docs.REQUESTED))
                .or(() -> estimate.flatMap(doc -> doc.money(Docs.SUBSIDY)));
        Optional<BigDecimal> costs = costs(facts, estimate);
        if (rate.isEmpty() || cap.isEmpty() || requested.isEmpty() || costs.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет запрошенной суммы, затрат или параметров программы"));
        }
        BigDecimal byRate = Money.round(rate.get().multiply(costs.get()));
        BigDecimal max = byRate.min(cap.get());
        List<Evidence> where = new ArrayList<>();
        app.ifPresent(doc -> where.add(Evidence.of(doc, Docs.REQUESTED)));
        estimate.ifPresent(doc -> where.add(Evidence.of(doc, Docs.TOTAL)));
        String formula = Money.format(max) + " = min(" + rate.get().toPlainString() + " × "
                + Money.format(costs.get()) + ", " + Money.format(cap.get()) + ")";
        return List.of(Money.greater(requested.get(), max)
                ? RuleResult.failed(CODE, REQUESTED_TEXT + Money.format(requested.get()) + ", максимум " + formula, where,
                Money.plain(max), Money.plain(requested.get()))
                : RuleResult.passed(CODE, REQUESTED_TEXT + Money.format(requested.get()) + " ≤ максимум " + formula,
                where));
    }

    private static Optional<BigDecimal> costs(PackageFacts facts, Optional<DocFacts> estimate) {
        Optional<BigDecimal> total = estimate.flatMap(doc -> doc.money(Docs.TOTAL));
        Optional<BigDecimal> confirmed = Sums.confirmed(facts);
        if (total.isPresent() && confirmed.isPresent()) {
            return Optional.of(total.get().min(confirmed.get()));
        }
        return total.or(() -> confirmed);
    }
}
