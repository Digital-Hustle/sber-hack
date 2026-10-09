package ru.subsidy.rules.engine.rules;

import java.util.ArrayList;
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
 * CROSS_NAME: наименование заявителя совпадает с ЕГРЮЛ (с нормализацией кавычек и ОПФ) — предупреждение.
 */
@Component
public class CrossNameRule implements Rule {

    private static final String CODE = "CROSS_NAME";
    private static final List<String> TYPES = List.of(Docs.APPLICATION, Docs.TAX, Docs.FIN, Docs.ESTIMATE);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> egrul = facts.first(Docs.EGRUL).filter(doc -> doc.text(Docs.ORG_NAME).isPresent());
        if (egrul.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет наименования в выписке ЕГРЮЛ"));
        }
        String expected = egrul.get().text(Docs.ORG_NAME).orElseThrow();
        List<RuleResult> warnings = new ArrayList<>();
        List<Evidence> checked = new ArrayList<>(List.of(Evidence.of(egrul.get(), Docs.ORG_NAME)));
        for (String type : TYPES) {
            for (DocFacts doc : facts.ofType(type)) {
                doc.text(Docs.ORG_NAME).ifPresent(name -> {
                    checked.add(Evidence.of(doc, Docs.ORG_NAME));
                    if (!Names.organization(name).equals(Names.organization(expected))) {
                        warnings.add(RuleResult.warning(CODE, "Наименование в документе «" + Docs.title(type)
                                + "» («" + name + "») отличается от ЕГРЮЛ («" + expected + "»)",
                                List.of(Evidence.of(doc, Docs.ORG_NAME), Evidence.of(egrul.get(), Docs.ORG_NAME))));
                    }
                });
            }
        }
        return warnings.isEmpty() ? List.of(RuleResult.passed(CODE, "Наименование совпадает с ЕГРЮЛ", checked))
                : warnings;
    }
}
