package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.money.Money;
import ru.subsidy.rules.program.ProgramParams;

/**
 * SUM_WORDS: сумма прописью (поле {@code <сумма>Words}, если есть) совпадает с суммой цифрами — предупреждение.
 */
@Component
public class SumWordsRule implements Rule {

    private static final String CODE = "SUM_WORDS";
    private static final String WORDS = "Words";
    private static final Map<String, String> SOURCES = Map.of(Docs.INVOICE, Docs.TOTAL, Docs.CONTRACT, Docs.AMOUNT,
            Docs.PAYMENT, Docs.AMOUNT);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        List<RuleResult> problems = new ArrayList<>();
        List<Evidence> matched = new ArrayList<>();
        SOURCES.forEach((type, field) -> facts.ofType(type).forEach(doc ->
                check(doc, field).ifPresentOrElse(problems::add, () -> {
                    if (doc.text(field + WORDS).isPresent() && doc.money(field).isPresent()) {
                        matched.add(Evidence.of(doc, field + WORDS));
                    }
                })));
        if (!problems.isEmpty()) {
            return problems;
        }
        return matched.isEmpty() ? List.of(RuleResult.notApplicable(CODE, "Суммы прописью в документах нет"))
                : List.of(RuleResult.passed(CODE, "Суммы прописью совпадают с цифрами", matched));
    }

    private static Optional<RuleResult> check(DocFacts doc, String field) {
        Optional<String> words = doc.text(field + WORDS);
        Optional<BigDecimal> digits = doc.money(field);
        if (words.isEmpty() || digits.isEmpty()) {
            return Optional.empty();
        }
        List<Evidence> evidence = List.of(Evidence.of(doc, field), Evidence.of(doc, field + WORDS));
        String title = Docs.title(doc.type());
        Optional<BigDecimal> spelled = RubleWords.parse(words.get());
        if (spelled.isEmpty()) {
            return Optional.of(new RuleResult(CODE, RuleStatus.WARNING, title
                    + ": сумму прописью не удалось разобрать — сверьте вручную", evidence, Money.plain(digits.get()),
                    words.get()));
        }
        return mismatch(title, digits.get(), spelled.get(), evidence);
    }

    private static Optional<RuleResult> mismatch(String title, BigDecimal digits, BigDecimal spelled,
                                                 List<Evidence> evidence) {
        if (spelled.compareTo(digits) == 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleResult(CODE, RuleStatus.WARNING, title + ": прописью " + Money.format(spelled)
                + ", цифрами " + Money.format(digits), evidence, Money.plain(digits), Money.plain(spelled)));
    }
}
