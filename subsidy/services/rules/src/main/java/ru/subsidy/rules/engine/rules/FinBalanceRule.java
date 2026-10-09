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
 * FIN_BALANCE: актив (1600) = пассив (1700) в балансе — предупреждение.
 */
@Component
public class FinBalanceRule implements Rule {

    private static final String CODE = "FIN_BALANCE";
    private static final String ASSETS = "assetsTotal";
    private static final String LIABILITIES = "liabilitiesTotal";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> fin = facts.first(Docs.FIN).filter(doc -> doc.money(ASSETS).isPresent()
                && doc.money(LIABILITIES).isPresent());
        if (fin.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет строк 1600 и 1700 в отчётности"));
        }
        BigDecimal assets = fin.get().money(ASSETS).orElseThrow();
        BigDecimal liabilities = fin.get().money(LIABILITIES).orElseThrow();
        List<Evidence> where = List.of(Evidence.of(fin.get(), ASSETS), Evidence.of(fin.get(), LIABILITIES));
        return List.of(Money.same(assets, liabilities)
                ? RuleResult.passed(CODE, "Баланс сходится: " + Money.format(assets), where)
                : RuleResult.warning(CODE, "Актив " + Money.format(assets) + " ≠ пассив " + Money.format(liabilities),
                where));
    }
}
