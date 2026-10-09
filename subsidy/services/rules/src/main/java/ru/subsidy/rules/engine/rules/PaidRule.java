package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.money.Money;
import ru.subsidy.rules.program.ProgramParams;

/**
 * SUM_PAID: Σ платежей ≥ Σ счетов, если программа возмещает только оплаченные затраты.
 */
@Component
public class PaidRule implements Rule {

    private static final String CODE = "SUM_PAID";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        if (!params.flag("reimbursePaidOnly")) {
            return List.of(RuleResult.notApplicable(CODE, "Программа возмещает и неоплаченные затраты"));
        }
        Optional<BigDecimal> costs = Sums.confirmed(facts);
        Optional<BigDecimal> paid = Sums.total(facts, Docs.PAYMENT, Docs.AMOUNT);
        if (costs.isEmpty() || paid.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет сумм в счетах или платёжных поручениях"));
        }
        return List.of(Money.greater(costs.get(), paid.get())
                ? RuleResult.failed(CODE, "Оплачено " + Money.format(paid.get()) + " из " + Money.format(costs.get()),
                List.of(), Money.plain(costs.get()), Money.plain(paid.get()))
                : RuleResult.passed(CODE, "Затраты оплачены: " + Money.format(paid.get()), List.of()));
    }
}
