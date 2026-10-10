package ru.subsidy.rules.engine.rules;

import java.util.ArrayList;
import java.util.List;

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
 * REQ_BANK_KEY: ключ счёта вместе с БИК своего банка.
 *
 * <p>У платёжного поручения два банка: счёт плательщика сверяется с БИК банка плательщика,
 * счёт получателя — с БИК банка получателя. Крест-накрест ключ не сойдётся и у корректного документа.</p>
 */
@Component
public class BankKeyRule implements Rule {

    private static final String CODE = "REQ_BANK_KEY";
    private static final List<Pair> PAIRS = List.of(
            new Pair(Docs.APPLICATION, "bankAccount", Docs.BIK),
            new Pair(Docs.PAYMENT, "payerAccount", Docs.PAYER_BIK),
            new Pair(Docs.PAYMENT, "payeeAccount", Docs.PAYEE_BIK));

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<RuleResult> failed = new ArrayList<>();
        List<Evidence> checked = new ArrayList<>();
        for (Pair pair : PAIRS) {
            for (DocFacts doc : facts.ofType(pair.type())) {
                String account = doc.text(pair.account()).map(Docs::digits).orElse("");
                String bik = doc.text(pair.bik()).map(Docs::digits).orElse("");
                if (!account.isEmpty() && !bik.isEmpty()) {
                    List<Evidence> where = List.of(Evidence.of(doc, pair.account()), Evidence.of(doc, pair.bik()));
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

    /**
     * Пара «счёт — БИК того же банка» в одном виде документа.
     *
     * @param type вид документа
     * @param account поле счёта
     * @param bik поле БИК
     */
    private record Pair(String type, String account, String bik) {
    }
}
