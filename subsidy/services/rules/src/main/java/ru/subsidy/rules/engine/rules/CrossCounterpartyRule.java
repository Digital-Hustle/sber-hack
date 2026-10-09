package ru.subsidy.rules.engine.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * CROSS_COUNTERPARTY: продавец в счёте = исполнитель договора = получатель платежа (по ИНН).
 */
@Component
public class CrossCounterpartyRule implements Rule {

    private static final String COMMA = ", ";
    private static final String CODE = "CROSS_COUNTERPARTY";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Set<String> sellers = new HashSet<>();
        List<Evidence> checked = new ArrayList<>();
        for (String type : List.of(Docs.CONTRACT, Docs.INVOICE)) {
            for (DocFacts doc : facts.ofType(type)) {
                doc.text(Docs.SELLER_INN).map(Docs::digits).ifPresent(inn -> {
                    sellers.add(inn);
                    checked.add(Evidence.of(doc, Docs.SELLER_INN));
                });
            }
        }
        List<RuleResult> failed = new ArrayList<>();
        for (DocFacts pay : facts.ofType(Docs.PAYMENT)) {
            pay.text(Docs.PAYEE_INN).map(Docs::digits).ifPresent(inn -> {
                checked.add(Evidence.of(pay, Docs.PAYEE_INN));
                if (!sellers.isEmpty() && !sellers.contains(inn)) {
                    failed.add(RuleResult.failed(CODE, "Получатель платежа (ИНН " + inn
                            + ") не совпадает с продавцом в счёте или договоре (" + String.join(COMMA, sellers) + ")",
                            List.of(Evidence.of(pay, Docs.PAYEE_INN)), String.join(COMMA, sellers), inn));
                }
            });
        }
        if (sellers.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет ИНН продавца в счетах или договорах"));
        }
        return failed.isEmpty() ? List.of(RuleResult.passed(CODE, "Продавец и получатель платежей совпадают",
                checked)) : failed;
    }
}
