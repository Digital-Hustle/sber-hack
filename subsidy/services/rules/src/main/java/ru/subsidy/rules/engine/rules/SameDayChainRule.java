package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.money.Money;
import ru.subsidy.rules.program.ProgramParams;

/**
 * ANOMALY_SAME_DAY_CHAIN: договор, счёт и оплата в один день на крупную сумму — предупреждение.
 */
@Component
public class SameDayChainRule implements Rule {

    private static final String CODE = "ANOMALY_SAME_DAY_CHAIN";
    private static final BigDecimal LARGE = new BigDecimal("1000000.00");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Set<LocalDate> contracts = dates(facts, Docs.CONTRACT);
        Set<LocalDate> invoices = dates(facts, Docs.INVOICE);
        Set<LocalDate> payments = dates(facts, Docs.PAYMENT);
        if (contracts.isEmpty() || invoices.isEmpty() || payments.isEmpty()) {
            return List.of(RuleResult.notApplicable(CODE, "Нет полной цепочки договор → счёт → оплата"));
        }
        Optional<LocalDate> same = contracts.stream().filter(invoices::contains).filter(payments::contains).findFirst();
        BigDecimal threshold = params.money("sameDayChainAmount").orElse(LARGE);
        BigDecimal amount = Sums.total(facts, Docs.PAYMENT, Docs.AMOUNT).orElse(BigDecimal.ZERO);
        boolean suspicious = same.isPresent() && amount.compareTo(threshold) >= 0;
        return List.of(suspicious ? RuleResult.warning(CODE, "Договор, счёт и оплата в один день " + same.get()
                + " на " + Money.format(amount), List.of())
                : RuleResult.passed(CODE, "Цепочка затрат без признаков «одного дня»", List.of()));
    }

    private static Set<LocalDate> dates(PackageFacts facts, String type) {
        return facts.ofType(type).stream().map(doc -> doc.date(Docs.DATE)).flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }
}
