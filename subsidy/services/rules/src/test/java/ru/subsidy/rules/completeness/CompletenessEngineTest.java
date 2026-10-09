package ru.subsidy.rules.completeness;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.FileFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.program.ChecklistItem;
import ru.subsidy.rules.program.Program;
import ru.subsidy.rules.program.ProgramParams;

class CompletenessEngineTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static Program program(boolean paidOnly) {
        return new Program("DEMO-2026", 1, "demo", true,
                new ProgramParams(JSON.valueToTree(Map.of("reimbursePaidOnly", paidOnly))),
                List.of(new ChecklistItem("APPLICATION", "Заявление", ChecklistItem.REQUIRED, List.of("APPLICATION"),
                                null, null, true),
                        new ChecklistItem("COST_PROOF", "Договор или счёт", ChecklistItem.ONE_OF,
                                List.of("CONTRACT", "INVOICE"), null, null, false),
                        new ChecklistItem("PAYMENTS", "Платёжки", ChecklistItem.CONDITIONAL, List.of("PAYMENT_ORDER"),
                                "reimbursePaidOnly", "только оплаченные", false)),
                "system", null);
    }

    private static DocFacts doc(String type, String reason) {
        return new DocFacts(UUID.randomUUID(), UUID.randomUUID(), type, BigDecimal.ONE, "PARSED", reason, 1, 1,
                Map.of());
    }

    private static PackageFacts facts(DocFacts... docs) {
        return new PackageFacts(UUID.randomUUID(), "7707083893", "DEMO-2026", LocalDate.of(2026, 3, 1), List.of(docs),
                List.of(new FileFacts(UUID.randomUUID(), 1, "PARSED", null)));
    }

    private static String status(Completeness result, String rule) {
        return result.results().stream().filter(res -> rule.equals(res.rule())).map(RuleResult::status)
                .findFirst().orElseThrow();
    }

    @Test
    void byDocumentType_notByFile() {
        Completeness full = new CompletenessEngine().check(facts(doc("APPLICATION", null), doc("INVOICE", null),
                doc("PAYMENT_ORDER", null)), program(true));
        assertThat(full.missing()).isEmpty();
        assertThat(full.percent()).isEqualTo(100);
        assertThat(status(full, "CHK_REQUIRED")).isEqualTo(RuleStatus.PASSED);
        assertThat(full.request()).isNull();
    }

    @Test
    void missingAndConditional() {
        Completeness partial = new CompletenessEngine().check(facts(doc("CONTRACT", null)), program(true));
        assertThat(partial.missing()).extracting(Missing::item).containsExactly("APPLICATION", "PAYMENTS");
        assertThat(partial.percent()).isEqualTo(33);
        assertThat(status(partial, "CHK_REQUIRED")).isEqualTo(RuleStatus.FAILED);
        assertThat(status(partial, "CHK_ONE_OF")).isEqualTo(RuleStatus.PASSED);
        assertThat(status(partial, "CHK_CONDITIONAL")).isEqualTo(RuleStatus.FAILED);
        assertThat(partial.request()).contains("Заявление").contains("Платёжное поручение");

        Completeness noCondition = new CompletenessEngine().check(facts(doc("APPLICATION", null),
                doc("CONTRACT", null)), program(false));
        assertThat(noCondition.missing()).isEmpty();
        assertThat(status(noCondition, "CHK_CONDITIONAL")).isEqualTo(RuleStatus.NOT_APPLICABLE);
    }

    @Test
    void unknownTypeDoesNotCount_duplicatesWarn() {
        Completeness result = new CompletenessEngine().check(facts(doc("APPLICATION", "TYPE_UNKNOWN"),
                doc("OTHER", "TYPE_OTHER"), doc("INVOICE", null)), program(false));
        assertThat(result.missing()).extracting(Missing::item).containsExactly("APPLICATION");
        assertThat(status(result, "CHK_UNKNOWN")).isEqualTo(RuleStatus.WARNING);
        Completeness dup = new CompletenessEngine().check(facts(doc("APPLICATION", null), doc("APPLICATION", null),
                doc("INVOICE", null)), program(false));
        assertThat(status(dup, "CHK_DUPLICATE")).isEqualTo(RuleStatus.WARNING);
    }
}
