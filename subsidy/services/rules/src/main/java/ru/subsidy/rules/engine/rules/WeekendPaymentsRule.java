package ru.subsidy.rules.engine.rules;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * ANOMALY_WEEKEND_DATES: платёж датирован выходным днём — предупреждение.
 */
@Component
public class WeekendPaymentsRule implements Rule {

    private static final String CODE = "ANOMALY_WEEKEND_DATES";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<RuleResult> warnings = new ArrayList<>();
        int checked = 0;
        for (DocFacts pay : facts.ofType(Docs.PAYMENT)) {
            if (pay.date(Docs.DATE).isPresent()) {
                checked++;
                DayOfWeek day = pay.date(Docs.DATE).orElseThrow().getDayOfWeek();
                if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                    warnings.add(RuleResult.warning(CODE, "Платёж от " + pay.date(Docs.DATE).orElseThrow()
                            + " датирован выходным днём", List.of(Evidence.of(pay, Docs.DATE))));
                }
            }
        }
        if (checked == 0) {
            return List.of(RuleResult.notApplicable(CODE, "Нет дат платежей"));
        }
        return warnings.isEmpty() ? List.of(RuleResult.passed(CODE, "Платежи в рабочие дни", List.of())) : warnings;
    }
}
