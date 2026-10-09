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
 * CROSS_SIGNER: подписант заявления = руководитель из ЕГРЮЛ (фамилия и инициалы).
 */
@Component
public class CrossSignerRule implements Rule {

    private static final String CODE = "CROSS_SIGNER";
    private static final String SIGNER = "signerName";
    private static final String HEAD = "headName";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<DocFacts> app = facts.first(Docs.APPLICATION).filter(doc -> doc.text(SIGNER).isPresent());
        Optional<DocFacts> egrul = facts.first(Docs.EGRUL).filter(doc -> doc.text(HEAD).isPresent());
        if (app.isEmpty() || egrul.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "Нет подписанта в заявлении или руководителя в ЕГРЮЛ"));
        }
        String signer = app.get().text(SIGNER).orElseThrow();
        String head = egrul.get().text(HEAD).orElseThrow();
        List<Evidence> where = List.of(Evidence.of(app.get(), SIGNER), Evidence.of(egrul.get(), HEAD));
        return List.of(RuleResult.check(CODE, Names.person(signer).equals(Names.person(head)),
                "Заявление подписал руководитель из ЕГРЮЛ",
                "Заявление подписал «" + signer + "», а руководитель по ЕГРЮЛ — «" + head
                        + "»; нужна доверенность", where));
    }
}
