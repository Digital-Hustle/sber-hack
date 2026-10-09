package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.money.Money;
import ru.subsidy.rules.program.ProgramParams;

/**
 * SUM_VAT: НДС в счёте = round(total × r / (100 + r)) для единой ставки 0/10/20 % (предупреждение).
 */
@Component
public class VatRule implements Rule {

    private static final String VAT_TEXT = "НДС ";
    private static final String CODE = "SUM_VAT";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<Integer> RATES = Set.of(0, 10, 20);
    private static final int MATH_SCALE = 10;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<RuleResult> results = new ArrayList<>();
        for (DocFacts doc : facts.ofType(Docs.INVOICE)) {
            Optional<BigDecimal> total = doc.money(Docs.TOTAL);
            Optional<BigDecimal> vat = doc.money(Docs.VAT);
            Optional<Integer> rate = rate(doc);
            if (total.isPresent() && vat.isPresent() && rate.isPresent()) {
                BigDecimal rateValue = BigDecimal.valueOf(rate.get());
                BigDecimal expected = Money.round(total.get().multiply(rateValue)
                        .divide(HUNDRED.add(rateValue), MATH_SCALE, java.math.RoundingMode.HALF_UP));
                List<Evidence> where = List.of(Evidence.of(doc, Docs.VAT), Evidence.of(doc, Docs.TOTAL));
                results.add(Money.same(expected, vat.get())
                        ? RuleResult.passed(CODE, VAT_TEXT + rate.get() + "% посчитан верно", where)
                        : RuleResult.warning(CODE, VAT_TEXT + rate.get() + "% от " + Money.format(total.get()) + " = "
                        + Money.format(expected) + ", а указано " + Money.format(vat.get()), where));
            }
        }
        return results.isEmpty() ? List.of(RuleResult.notApplicable(CODE, "Нет счетов с НДС и единой ставкой"))
                : results;
    }

    private static Optional<Integer> rate(DocFacts doc) {
        Set<Integer> rates = new HashSet<>();
        for (JsonNode row : doc.rows(Docs.ITEMS)) {
            String text = row.path("vatRate").asText("");
            String digits = Docs.digits(text);
            if (text.toLowerCase(java.util.Locale.ROOT).contains("без")) {
                rates.add(0);
            } else if (!digits.isEmpty()) {
                rates.add(Integer.parseInt(digits));
            }
        }
        return rates.size() == 1 && RATES.containsAll(rates) ? rates.stream().findFirst() : Optional.empty();
    }
}
