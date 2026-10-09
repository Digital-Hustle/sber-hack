package ru.subsidy.rules.engine.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
 * DATE_ORDER: договор ≤ счёт ≤ платёж (предупреждение).
 */
@Component
public class DateOrderRule implements Rule {

    private static final String CODE = "DATE_ORDER";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> contract = earliest(facts, Docs.CONTRACT);
        Optional<DocFacts> invoice = earliest(facts, Docs.INVOICE);
        Optional<DocFacts> payment = earliest(facts, Docs.PAYMENT);
        List<RuleResult> warnings = new ArrayList<>();
        if (contract.isPresent() && invoice.isPresent() && before(invoice.get(), contract.get())) {
            warnings.add(RuleResult.warning(CODE, "Счёт от " + date(invoice.get()) + " раньше договора от "
                    + date(contract.get()), List.of(Evidence.of(invoice.get(), Docs.DATE),
                    Evidence.of(contract.get(), Docs.DATE))));
        }
        if (invoice.isPresent() && payment.isPresent() && before(payment.get(), invoice.get())) {
            warnings.add(RuleResult.warning(CODE, "Оплата от " + date(payment.get()) + " раньше счёта от "
                    + date(invoice.get()), List.of(Evidence.of(payment.get(), Docs.DATE),
                    Evidence.of(invoice.get(), Docs.DATE))));
        }
        long known = List.of(contract, invoice, payment).stream().filter(Optional::isPresent).count();
        if (known < 2) {
            return List.of(RuleResult.missing(CODE, "Для порядка дат нужны хотя бы два из: договор, счёт, платёж"));
        }
        return warnings.isEmpty() ? List.of(RuleResult.passed(CODE, "Даты идут по порядку: договор → счёт → оплата",
                List.of())) : warnings;
    }

    private static Optional<DocFacts> earliest(PackageFacts facts, String type) {
        return facts.ofType(type).stream().filter(doc -> doc.date(Docs.DATE).isPresent())
                .min(Comparator.comparing(doc -> doc.date(Docs.DATE).orElseThrow()));
    }

    private static boolean before(DocFacts left, DocFacts right) {
        return date(left).isBefore(date(right));
    }

    private static LocalDate date(DocFacts doc) {
        return doc.date(Docs.DATE).orElseThrow();
    }
}
