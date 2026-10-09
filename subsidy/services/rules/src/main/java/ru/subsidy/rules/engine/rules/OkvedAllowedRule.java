package ru.subsidy.rules.engine.rules;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * ORG_OKVED_ALLOWED: основной ОКВЭД из ЕГРЮЛ в списке программы («10.*», «62.01»).
 */
@Component
public class OkvedAllowedRule implements Rule {

    private static final String DOT = ".";
    private static final String OKVED_TEXT = "ОКВЭД ";
    private static final String CODE = "ORG_OKVED_ALLOWED";
    private static final String OKVED = "okvedMain";
    private static final Pattern CODE_PATTERN = Pattern.compile("\\d{2}(\\.\\d{1,2}){0,2}");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<String> allowed = params.list("okvedAllowed");
        if (allowed.isEmpty()) {
            return List.of(RuleResult.notApplicable(CODE, "Программа не ограничивает ОКВЭД"));
        }
        Optional<DocFacts> egrul = facts.first(Docs.EGRUL);
        Optional<String> okved = egrul.flatMap(doc -> doc.text(OKVED)).flatMap(OkvedAllowedRule::extract);
        if (okved.isEmpty()) {
            return List.of(RuleResult.missing(CODE, "В выписке ЕГРЮЛ не найден основной ОКВЭД"));
        }
        boolean match = allowed.stream().anyMatch(mask -> matches(okved.get(), mask));
        return List.of(RuleResult.check(CODE, match, OKVED_TEXT + okved.get() + " разрешён программой",
                OKVED_TEXT + okved.get() + " не входит в список программы: " + String.join(", ", allowed),
                List.of(Evidence.of(egrul.orElseThrow(), OKVED))));
    }

    private static Optional<String> extract(String text) {
        Matcher matcher = CODE_PATTERN.matcher(text);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    /**
     * Совпадение кода с маской «10.*» или точным кодом.
     *
     * @param okved код
     * @param mask маска
     * @return true, если подходит
     */
    static boolean matches(String okved, String mask) {
        if (mask.endsWith(".*")) {
            String prefix = mask.substring(0, mask.length() - 2);
            return okved.equals(prefix) || okved.startsWith(prefix + DOT);
        }
        return okved.equals(mask) || okved.startsWith(mask + DOT);
    }
}
