package ru.subsidy.rules.engine.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * CROSS_INN: ИНН заявителя одинаков во всех документах, где он указан.
 */
@Component
public class CrossInnRule implements Rule {

    private static final String CLOSE = ")";
    private static final String CODE = "CROSS_INN";
    private static final Map<String, String> SOURCES = Map.of(
            Docs.APPLICATION, Docs.INN, Docs.EGRUL, Docs.INN, Docs.TAX, Docs.INN, Docs.FIN, Docs.INN,
            Docs.ESTIMATE, Docs.INN, Docs.CONTRACT, Docs.BUYER_INN, Docs.INVOICE, Docs.BUYER_INN,
            Docs.PAYMENT, Docs.PAYER_INN);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        String applicant = Docs.digits(facts.inn());
        List<RuleResult> failed = new ArrayList<>();
        List<Evidence> checked = new ArrayList<>();
        for (DocFacts doc : facts.documents()) {
            String field = SOURCES.get(doc.type());
            if (field != null && !applicant.isEmpty()) {
                doc.text(field).map(Docs::digits).ifPresent(inn -> {
                    checked.add(Evidence.of(doc, field));
                    if (!applicant.equals(inn)) {
                        failed.add(RuleResult.failed(CODE, "ИНН в документе «" + Docs.title(doc.type()) + "» (" + inn
                                + ") ≠ ИНН заявителя (" + applicant + CLOSE, List.of(Evidence.of(doc, field)),
                                applicant, inn));
                    }
                });
            }
        }
        if (checked.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет ИНН заявителя в документах для сверки"));
        }
        return failed.isEmpty() ? List.of(RuleResult.passed(CODE, "ИНН заявителя совпадает во всех документах ("
                + checked.size() + CLOSE, checked)) : failed;
    }
}
