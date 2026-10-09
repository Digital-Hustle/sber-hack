package ru.subsidy.rules.engine.rules;

import java.util.List;
import java.util.Locale;
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
 * ORG_ACTIVE: по ЕГРЮЛ организация действующая, нет ликвидации и банкротства.
 */
@Component
public class OrgActiveRule implements Rule {

    private static final String CODE = "ORG_ACTIVE";
    private static final String STATUS = "status";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> egrul = facts.first(Docs.EGRUL).filter(doc -> doc.text(STATUS).isPresent());
        if (egrul.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "В выписке ЕГРЮЛ не найден статус организации"));
        }
        String status = egrul.get().text(STATUS).orElseThrow();
        String lower = status.toLowerCase(Locale.ROOT);
        boolean stopped = lower.matches(".*(ликвид|банкрот|прекращ|исключ|реорганиз).*");
        return List.of(RuleResult.check(CODE, !stopped, "Организация действующая",
                "Статус по ЕГРЮЛ: «" + status + "»", List.of(Evidence.of(egrul.get(), STATUS))));
    }
}
