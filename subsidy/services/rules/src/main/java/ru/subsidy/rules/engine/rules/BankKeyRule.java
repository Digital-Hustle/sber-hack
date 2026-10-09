package ru.subsidy.rules.engine.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.Requisites;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * REQ_BANK_KEY: ключ расчётного счёта вместе с БИК (заявление, платёжные поручения).
 */
@Component
public class BankKeyRule implements Rule {

    private static final String CODE = "REQ_BANK_KEY";
    private static final Map<String, String> ACCOUNTS = Map.of(Docs.APPLICATION, "bankAccount",
            Docs.PAYMENT, "payerAccount");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<RuleResult> failed = new ArrayList<>();
        List<Evidence> checked = new ArrayList<>();
        for (Map.Entry<String, String> entry : ACCOUNTS.entrySet()) {
            for (DocFacts doc : facts.ofType(entry.getKey())) {
                String account = doc.text(entry.getValue()).map(Docs::digits).orElse("");
                String bik = doc.text(Docs.BIK).map(Docs::digits).orElse("");
                if (!account.isEmpty() && !bik.isEmpty()) {
                    List<Evidence> where = List.of(Evidence.of(doc, entry.getValue()), Evidence.of(doc, Docs.BIK));
                    checked.addAll(where);
                    if (!Requisites.account(account, bik)) {
                        failed.add(RuleResult.failed(CODE, "Счёт " + account + " не сходится с БИК " + bik
                                + " в документе «" + Docs.title(doc.type()) + "»", where, "ключ счёта по БИК",
                                account));
                    }
                }
            }
        }
        if (checked.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет пары «счёт + БИК» в заявлении или платёжках"));
        }
        return failed.isEmpty() ? List.of(RuleResult.passed(CODE, "Ключ счёта по БИК верен", checked)) : failed;
    }
}
