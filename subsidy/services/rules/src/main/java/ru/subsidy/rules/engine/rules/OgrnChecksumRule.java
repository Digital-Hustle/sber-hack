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
 * REQ_OGRN_CHECKSUM: контрольная цифра ОГРН/ОГРНИП.
 */
@Component
public class OgrnChecksumRule implements Rule {

    private static final String CODE = "REQ_OGRN_CHECKSUM";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<RuleResult> failed = new ArrayList<>();
        List<Evidence> checked = new ArrayList<>();
        for (DocFacts doc : facts.documents()) {
            doc.text(Docs.OGRN).map(Docs::digits).ifPresent(ogrn -> {
                checked.add(Evidence.of(doc, Docs.OGRN));
                if (!Requisites.ogrn(ogrn)) {
                    failed.add(RuleResult.failed(CODE, "ОГРН " + ogrn + " в документе «" + Docs.title(doc.type())
                            + "» не прошёл проверку контрольной цифры", List.of(Evidence.of(doc, Docs.OGRN)),
                            "верная контрольная цифра", ogrn));
                }
            });
        }
        if (checked.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "ОГРН в документах не найден"));
        }
        return failed.isEmpty() ? List.of(RuleResult.passed(CODE, "ОГРН верен", checked)) : failed;
    }
}
