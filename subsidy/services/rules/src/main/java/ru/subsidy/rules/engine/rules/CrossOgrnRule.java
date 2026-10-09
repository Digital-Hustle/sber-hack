package ru.subsidy.rules.engine.rules;

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
 * CROSS_OGRN: ОГРН в заявлении = ОГРН в ЕГРЮЛ.
 */
@Component
public class CrossOgrnRule implements Rule {

    private static final String CODE = "CROSS_OGRN";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> app = facts.first(Docs.APPLICATION).filter(doc -> doc.text(Docs.OGRN).isPresent());
        Optional<DocFacts> egrul = facts.first(Docs.EGRUL).filter(doc -> doc.text(Docs.OGRN).isPresent());
        if (app.isEmpty() || egrul.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет ОГРН в заявлении или выписке ЕГРЮЛ"));
        }
        String left = Docs.digits(app.get().text(Docs.OGRN).orElseThrow());
        String right = Docs.digits(egrul.get().text(Docs.OGRN).orElseThrow());
        List<Evidence> where = List.of(Evidence.of(app.get(), Docs.OGRN), Evidence.of(egrul.get(), Docs.OGRN));
        return List.of(left.equals(right) ? RuleResult.passed(CODE, "ОГРН совпадает с ЕГРЮЛ", where)
                : RuleResult.failed(CODE, "ОГРН в заявлении (" + left + ") ≠ ОГРН в ЕГРЮЛ (" + right + ")", where,
                right, left));
    }
}
