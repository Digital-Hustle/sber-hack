package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * SUM_INVOICE_ITEMS: Σ позиций = итог счёта; qty × price = amount по каждой позиции (± 0.01).
 */
@Component
public class InvoiceItemsRule implements Rule {

    private static final String CODE = "SUM_INVOICE_ITEMS";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<RuleResult> failed = new ArrayList<>();
        int checked = 0;
        for (DocFacts doc : facts.ofType(Docs.INVOICE)) {
            Optional<BigDecimal> total = doc.money(Docs.TOTAL);
            Optional<BigDecimal> sum = Sums.items(doc);
            if (total.isPresent() && sum.isPresent()) {
                checked++;
                if (!Money.same(sum.get(), total.get())) {
                    failed.add(RuleResult.failed(CODE, "Сумма позиций " + Money.format(sum.get()) + " ≠ итог "
                            + Money.format(total.get()), List.of(Evidence.of(doc, Docs.ITEMS),
                            Evidence.of(doc, Docs.TOTAL)), Money.plain(total.get()), Money.plain(sum.get())));
                }
            }
            failed.addAll(rows(doc));
        }
        if (checked == 0 && failed.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет счетов с позициями и итогом"));
        }
        return failed.isEmpty() ? List.of(RuleResult.passed(CODE, "Суммы позиций сходятся с итогом", List.of()))
                : failed;
    }

    private static List<RuleResult> rows(DocFacts doc) {
        List<RuleResult> failed = new ArrayList<>();
        int index = 0;
        for (JsonNode row : doc.rows(Docs.ITEMS)) {
            index++;
            Optional<BigDecimal> qty = Sums.qty(row);
            Optional<BigDecimal> price = Sums.money(row, "price");
            Optional<BigDecimal> amount = Sums.money(row, Docs.AMOUNT);
            if (qty.isPresent() && price.isPresent() && amount.isPresent()) {
                BigDecimal expected = Money.round(qty.get().multiply(price.get()));
                if (!Money.same(expected, amount.get())) {
                    failed.add(RuleResult.failed(CODE, "Позиция " + index + ": " + qty.get().stripTrailingZeros()
                            .toPlainString() + " × " + Money.format(price.get()) + " = " + Money.format(expected)
                            + ", а указано " + Money.format(amount.get()), List.of(Evidence.of(doc, Docs.ITEMS)),
                            Money.plain(expected), Money.plain(amount.get())));
                }
            }
        }
        return failed;
    }
}
