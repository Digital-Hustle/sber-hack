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
 * REQ_INN_CHECKSUM: контрольная сумма каждого ИНН в каждом документе.
 */
@Component
public class InnChecksumRule implements Rule {

    private static final String CODE = "REQ_INN_CHECKSUM";
    private static final List<String> FIELDS = List.of(Docs.INN, Docs.BUYER_INN, Docs.SELLER_INN, Docs.PAYER_INN,
            Docs.PAYEE_INN);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<RuleResult> failed = new ArrayList<>();
        List<Evidence> checked = new ArrayList<>();
        for (DocFacts doc : facts.documents()) {
            for (String field : FIELDS) {
                doc.text(field).map(Docs::digits).ifPresent(inn -> {
                    checked.add(Evidence.of(doc, field));
                    if (!Requisites.inn(inn)) {
                        failed.add(RuleResult.failed(CODE, "ИНН " + inn + " в документе «" + Docs.title(doc.type())
                                + "» не прошёл проверку контрольной суммы", List.of(Evidence.of(doc, field)),
                                "верная контрольная сумма", inn));
                    }
                });
            }
        }
        if (checked.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "В документах не найден ни один ИНН"));
        }
        return failed.isEmpty() ? List.of(RuleResult.passed(CODE, "Контрольные суммы ИНН верны (" + checked.size()
                + ")", checked)) : failed;
    }
}
