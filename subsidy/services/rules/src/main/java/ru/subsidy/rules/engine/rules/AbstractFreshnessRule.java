package ru.subsidy.rules.engine.rules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import ru.subsidy.rules.engine.Evidence;
import ru.subsidy.rules.engine.Rule;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ProgramParams;

/**
 * Свежесть документа: packageDate − дата документа ≤ maxAgeDays из параметров программы.
 */
abstract class AbstractFreshnessRule implements Rule {

    private static final String DAYS = " дн.";
    private static final String FROM = " от ";

    private final String code;
    private final String type;
    private final String field;
    private final String param;
    private final String title;

    /**
     * Конструктор.
     *
     * @param code код правила
     * @param type вид документа
     * @param field поле даты
     * @param param параметр программы
     * @param title название в тексте
     */
    AbstractFreshnessRule(String code, String type, String field, String param, String title) {
        this.code = code;
        this.type = type;
        this.field = field;
        this.param = param;
        this.title = title;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public List<RuleResult> evaluate(PackageFacts facts, ProgramParams params) {
        Optional<Integer> maxAge = params.integer(param);
        Optional<DocFacts> doc = facts.first(type).filter(found -> found.date(field).isPresent());
        if (maxAge.isEmpty() || doc.isEmpty()) {
            return List.of(RuleResult.missing(code, maxAge.isEmpty() ? "В программе не задан срок " + param
                    : "Нет даты в документе «" + title + "»"));
        }
        LocalDate date = doc.get().date(field).orElseThrow();
        long age = ChronoUnit.DAYS.between(date, facts.packageDate());
        List<Evidence> where = List.of(Evidence.of(doc.get(), field));
        return List.of(age <= maxAge.get()
                ? RuleResult.passed(code, title + FROM + date + " — " + age + " дн., срок " + maxAge.get() + DAYS,
                where)
                : RuleResult.failed(code, title + FROM + date + " старше " + maxAge.get() + " дн. (" + age
                + " дн. на дату пакета " + facts.packageDate() + ")", where, "≤ " + maxAge.get() + DAYS,
                age + DAYS));
    }
}
