package ru.subsidy.rules.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.subsidy.rules.engine.rules.Fixture.params;
import static ru.subsidy.rules.engine.rules.Fixture.pkg;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;

class SumWordsRuleTest {

    private final SumWordsRule rule = new SumWordsRule();

    @Test
    void parsesRussianAmounts() {
        assertThat(RubleWords.parse("Один миллион двести пятьдесят тысяч рублей 00 копеек"))
                .contains(new BigDecimal("1250000.00"));
        assertThat(RubleWords.parse("двадцать одна тысяча триста рублей пятьдесят копеек"))
                .contains(new BigDecimal("21300.50"));
        assertThat(RubleWords.parse("Сто тысяч рублей")).contains(new BigDecimal("100000.00"));
        assertThat(RubleWords.parse("1 250 000 руб. 15 коп.")).contains(new BigDecimal("1250000.15"));
        assertThat(RubleWords.parse("тысяча рублей")).contains(new BigDecimal("1000.00"));
        assertThat(RubleWords.parse("много денег")).isEmpty();
    }

    @Test
    void matchingWords_passed() {
        List<RuleResult> results = rule.evaluate(pkg().doc(Docs.INVOICE, "total", "120000.00",
                "totalWords", "Сто двадцать тысяч рублей 00 копеек").facts(), params());
        assertThat(results).singleElement().extracting(RuleResult::status).isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void mismatch_warningWithExpectedAndActual() {
        List<RuleResult> results = rule.evaluate(pkg().doc(Docs.PAYMENT, "amount", "120000.00",
                "amountWords", "Сто двадцать одна тысяча рублей").facts(), params());
        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(RuleStatus.WARNING);
            assertThat(result.expected()).isEqualTo("120000.00");
            assertThat(result.actual()).isEqualTo("121000.00");
        });
    }

    @Test
    void noWords_notApplicable() {
        List<RuleResult> results = rule.evaluate(pkg().doc(Docs.CONTRACT, "amount", "5000.00").facts(), params());
        assertThat(results).singleElement().extracting(RuleResult::status).isEqualTo(RuleStatus.NOT_APPLICABLE);
    }
}
