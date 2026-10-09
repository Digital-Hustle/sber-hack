package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * ANOMALY_ROUND_SUMS: все суммы затрат круглые (кратны 1000) — предупреждение, решает специалист.
 */
@Component
public class RoundSumsRule implements Rule {

    private static final String CODE = "ANOMALY_ROUND_SUMS";
    private static final BigDecimal ROUND = BigDecimal.valueOf(1000);
    private static final int MIN_SUMS = 3;
    private static final Map<String, String> SOURCES = Map.of(Docs.INVOICE, Docs.TOTAL, Docs.CONTRACT, Docs.AMOUNT,
            Docs.PAYMENT, Docs.AMOUNT);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<BigDecimal> sums = new ArrayList<>();
        SOURCES.forEach((type, field) -> facts.ofType(type)
                .forEach(doc -> doc.money(field).ifPresent(sums::add)));
        if (sums.size() < MIN_SUMS) {
            return List.of(RuleResult.notApplicable(CODE, "Мало сумм для оценки"));
        }
        boolean allRound = sums.stream().allMatch(sum -> sum.remainder(ROUND).signum() == 0);
        return List.of(allRound ? RuleResult.warning(CODE, "Все суммы затрат круглые (" + sums.size()
                + ") — проверьте происхождение", List.of())
                : RuleResult.passed(CODE, "Суммы не вызывают подозрений", List.of()));
    }
}
