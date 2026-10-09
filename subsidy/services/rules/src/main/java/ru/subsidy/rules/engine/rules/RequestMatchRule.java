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
 * SUM_REQUEST_MATCH: запрошенная сумма в заявлении = сумма субсидии в смете.
 */
@Component
public class RequestMatchRule implements Rule {

    private static final String CODE = "SUM_REQUEST_MATCH";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> app = facts.first(Docs.APPLICATION);
        Optional<DocFacts> estimate = facts.first(Docs.ESTIMATE);
        Optional<BigDecimal> requested = app.flatMap(doc -> doc.money(Docs.REQUESTED));
        Optional<BigDecimal> subsidy = estimate.flatMap(doc -> doc.money(Docs.SUBSIDY));
        if (requested.isEmpty() || subsidy.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет запрошенной суммы в заявлении или суммы субсидии в смете"));
        }
        List<Evidence> where = List.of(Evidence.of(app.get(), Docs.REQUESTED), Evidence.of(estimate.get(),
                Docs.SUBSIDY));
        return List.of(Money.same(requested.get(), subsidy.get())
                ? RuleResult.passed(CODE, "Сумма в заявлении совпадает со сметой", where)
                : RuleResult.failed(CODE, "В заявлении " + Money.format(requested.get()) + ", в смете "
                + Money.format(subsidy.get()), where, Money.plain(subsidy.get()), Money.plain(requested.get())));
    }
}
