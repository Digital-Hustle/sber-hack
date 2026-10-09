package ru.subsidy.rules.engine.rules;

import java.time.LocalDate;
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
import ru.subsidy.rules.program.ProgramParams;

/**
 * DATE_COST_PERIOD: даты затрат (счета, платежи) в периоде программы [costFrom, costTo].
 */
@Component
public class CostPeriodRule implements Rule {

    private static final String DASH = " — ";
    private static final String CODE = "DATE_COST_PERIOD";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<LocalDate> from = params.date("costFrom");
        Optional<LocalDate> till = params.date("costTo");
        if (from.isEmpty() || till.isEmpty()) {
            return List.of(RuleResult.notApplicable(CODE, "Период затрат в программе не задан"));
        }
        List<RuleResult> failed = new ArrayList<>();
        List<Evidence> checked = new ArrayList<>();
        for (String type : List.of(Docs.INVOICE, Docs.PAYMENT)) {
            for (DocFacts doc : facts.ofType(type)) {
                doc.date(Docs.DATE).ifPresent(date -> {
                    checked.add(Evidence.of(doc, Docs.DATE));
                    if (date.isBefore(from.get()) || date.isAfter(till.get())) {
                        failed.add(RuleResult.failed(CODE, Docs.title(type) + " от " + date
                                + " вне периода затрат программы " + from.get() + DASH + till.get(),
                                List.of(Evidence.of(doc, Docs.DATE)), from.get() + DASH + till.get(),
                                date.toString()));
                    }
                });
            }
        }
        if (checked.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет дат в счетах и платёжках"));
        }
        return failed.isEmpty() ? List.of(RuleResult.passed(CODE, "Затраты в периоде программы", checked)) : failed;
    }
}
