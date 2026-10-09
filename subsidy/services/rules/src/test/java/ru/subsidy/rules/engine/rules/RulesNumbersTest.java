package ru.subsidy.rules.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.subsidy.rules.engine.rules.Fixture.items;
import static ru.subsidy.rules.engine.rules.Fixture.params;
import static ru.subsidy.rules.engine.rules.Fixture.pkg;

import java.util.List;

import org.junit.jupiter.api.Test;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.engine.RuleResult;
import ru.subsidy.rules.engine.RuleStatus;
import ru.subsidy.rules.facts.PackageFacts;

class RulesNumbersTest {

    private static <T> T rule(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException broken) {
            throw new IllegalStateException(broken);
        }
    }

    private static String status(List<RuleResult> results) {
        return results.stream().map(RuleResult::status).reduce((left, right) ->
                RuleStatus.FAILED.equals(left) ? left : right).orElseThrow();
    }

    @Test
    void subsidyCap_minOfRateAndMax() {
        // 0.50 × 4 000 000 = 2 000 000 < 3 000 000 → максимум 2 000 000
        PackageFacts valid = pkg().doc(Docs.APPLICATION, "requestedAmount", "2000000.00")
                .doc(Docs.ESTIMATE, "total", "4000000.00").facts();
        assertThat(status(rule(SubsidyCapRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        PackageFacts kopeck = pkg().doc(Docs.APPLICATION, "requestedAmount", "2000000.02")
                .doc(Docs.ESTIMATE, "total", "4000000.00").facts();
        List<RuleResult> failed = rule(SubsidyCapRule.class).evaluate(kopeck, params());
        assertThat(status(failed)).isEqualTo(RuleStatus.FAILED);
        assertThat(failed.get(0).message()).contains("2 000 000,02").contains("min(0.50 × 4 000 000,00, 3 000 000,00)");
        assertThat(failed.get(0).expected()).isEqualTo("2000000.00");
        // потолок: 0.50 × 8 000 000 = 4 000 000 > 3 000 000
        PackageFacts capped = pkg().doc(Docs.APPLICATION, "requestedAmount", "3500000.00")
                .doc(Docs.ESTIMATE, "total", "8000000.00").facts();
        assertThat(rule(SubsidyCapRule.class).evaluate(capped, params()).get(0).expected()).isEqualTo("3000000.00");
    }

    @Test
    void subsidyCap_noData_isDataMissingNotPassed() {
        assertThat(status(rule(SubsidyCapRule.class).evaluate(pkg().facts(), params()))).isEqualTo(RuleStatus.DATA_MISSING);
    }

    @Test
    void invoiceItems_sumAndRows() {
        PackageFacts valid = pkg().doc(Docs.INVOICE, "total", "350.50",
                "items", items(2, "100.00", "200.00", 3, "50.17", "150.50")).facts();
        assertThat(status(rule(InvoiceItemsRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        PackageFacts bad = pkg().doc(Docs.INVOICE, "total", "350.52",
                "items", items(2, "100.00", "200.00", 3, "50.17", "150.50")).facts();
        assertThat(status(rule(InvoiceItemsRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
        PackageFacts row = pkg().doc(Docs.INVOICE, "total", "210.00", "items", items(2, "100.00", "210.00")).facts();
        assertThat(rule(InvoiceItemsRule.class).evaluate(row, params()).get(0).message()).contains("Позиция 1");
    }

    @Test
    void vat_includedRate20() {
        // 120.00 с НДС 20 % → НДС 20.00
        PackageFacts valid = pkg().doc(Docs.INVOICE, "total", "120.00", "vatAmount", "20.00",
                "items", items(1, "120.00", "120.00")).facts();
        assertThat(status(rule(VatRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        PackageFacts bad = pkg().doc(Docs.INVOICE, "total", "120.00", "vatAmount", "24.00",
                "items", items(1, "120.00", "120.00")).facts();
        assertThat(status(rule(VatRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.WARNING);
    }

    @Test
    void estimate_totalAndConfirmed() {
        PackageFacts valid = pkg().doc(Docs.ESTIMATE, "total", "300.00", "items", items(1, "100.00", "100.00", 1,
                "200.00", "200.00")).doc(Docs.INVOICE, "total", "300.00").facts();
        assertThat(status(rule(EstimateTotalRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        assertThat(status(rule(EstimateConfirmedRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        PackageFacts bad = pkg().doc(Docs.ESTIMATE, "total", "300.00", "items", items(1, "100.00", "100.00"))
                .doc(Docs.INVOICE, "total", "299.98").facts();
        assertThat(status(rule(EstimateTotalRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
        assertThat(status(rule(EstimateConfirmedRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
    }

    @Test
    void paid_andRequestMatch() {
        PackageFacts valid = pkg().doc(Docs.INVOICE, "total", "1000.00").doc(Docs.PAYMENT, "amount", "600.00")
                .doc(Docs.PAYMENT, "amount", "400.00").doc(Docs.APPLICATION, "requestedAmount", "500.00")
                .doc(Docs.ESTIMATE, "subsidyAmount", "500.00").facts();
        assertThat(status(rule(PaidRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        assertThat(status(rule(RequestMatchRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        PackageFacts bad = pkg().doc(Docs.INVOICE, "total", "1000.00").doc(Docs.PAYMENT, "amount", "999.98")
                .doc(Docs.APPLICATION, "requestedAmount", "500.00").doc(Docs.ESTIMATE, "subsidyAmount", "500.02")
                .facts();
        assertThat(status(rule(PaidRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
        assertThat(status(rule(RequestMatchRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
    }

    @Test
    void requisites_checksums() {
        PackageFacts valid = pkg().doc(Docs.APPLICATION, "inn", "7707083893", "ogrn", "1027700132195",
                "bankAccount", "40702810400000000001", "bik", "044525225").facts();
        assertThat(status(rule(InnChecksumRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        assertThat(status(rule(OgrnChecksumRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        PackageFacts bad = pkg().doc(Docs.APPLICATION, "inn", "7707083894", "ogrn", "1027700132196").facts();
        assertThat(status(rule(InnChecksumRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
        assertThat(status(rule(OgrnChecksumRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
        assertThat(status(rule(BankKeyRule.class).evaluate(pkg().facts(), params()))).isEqualTo(RuleStatus.DATA_MISSING);
    }

    @Test
    void cross_innNameSignerCounterparty() {
        PackageFacts valid = pkg()
                .doc(Docs.APPLICATION, "inn", "7707083893", "orgName", "ООО «Ромашка»", "signerName", "Иванов И. И.")
                .doc(Docs.EGRUL, "inn", "7707083893", "orgName", "Общество с ограниченной ответственностью \"Ромашка\"",
                        "headName", "Иванов Иван Иванович")
                .doc(Docs.INVOICE, "buyerInn", "7707083893", "sellerInn", "5408131553")
                .doc(Docs.PAYMENT, "payerInn", "7707083893", "payeeInn", "5408131553").facts();
        assertThat(status(rule(CrossInnRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        assertThat(status(rule(CrossNameRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        assertThat(status(rule(CrossSignerRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        assertThat(status(rule(CrossCounterpartyRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        PackageFacts bad = pkg().doc(Docs.APPLICATION, "inn", "7707083893", "signerName", "Петров П. П.")
                .doc(Docs.EGRUL, "headName", "Иванов Иван Иванович")
                .doc(Docs.TAX, "inn", "5408131553").doc(Docs.INVOICE, "sellerInn", "5408131553")
                .doc(Docs.PAYMENT, "payeeInn", "7736050003").facts();
        List<RuleResult> inn = rule(CrossInnRule.class).evaluate(bad, params());
        assertThat(status(inn)).isEqualTo(RuleStatus.FAILED);
        assertThat(inn.get(0).message()).contains("Справка ФНС").contains("5408131553");
        assertThat(status(rule(CrossSignerRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
        assertThat(status(rule(CrossCounterpartyRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
    }

    @Test
    void dates_freshnessBoundary() {
        // дата пакета 2026-03-20: 30 дней → 2026-02-18 ещё можно, 2026-02-17 — уже нет
        PackageFacts edge = pkg().doc(Docs.EGRUL, "extractDate", "2026-02-18")
                .doc(Docs.TAX, "certificateDate", "2026-02-17", "hasDebt", false).facts();
        assertThat(status(rule(EgrulFreshRule.class).evaluate(edge, params()))).isEqualTo(RuleStatus.PASSED);
        assertThat(status(rule(TaxFreshRule.class).evaluate(edge, params()))).isEqualTo(RuleStatus.FAILED);
        assertThat(status(rule(TaxNoDebtRule.class).evaluate(edge, params()))).isEqualTo(RuleStatus.PASSED);
        PackageFacts debt = pkg().doc(Docs.TAX, "debtAmount", "1500.00").facts();
        assertThat(status(rule(TaxNoDebtRule.class).evaluate(debt, params()))).isEqualTo(RuleStatus.FAILED);
    }

    @Test
    void dates_orderPeriodOrg() {
        PackageFacts bad = pkg().doc(Docs.CONTRACT, "date", "2026-03-10").doc(Docs.INVOICE, "date", "2026-03-01")
                .doc(Docs.PAYMENT, "date", "2025-12-30").doc(Docs.EGRUL, "status", "Ликвидация",
                        "okvedMain", "47.11 Торговля розничная").facts();
        assertThat(status(rule(DateOrderRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.WARNING);
        assertThat(status(rule(CostPeriodRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
        assertThat(status(rule(OrgActiveRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
        assertThat(status(rule(OkvedAllowedRule.class).evaluate(bad, params()))).isEqualTo(RuleStatus.FAILED);
        PackageFacts valid = pkg().doc(Docs.EGRUL, "status", "Действующее", "okvedMain", "62.01 Разработка ПО").facts();
        assertThat(status(rule(OrgActiveRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        assertThat(status(rule(OkvedAllowedRule.class).evaluate(valid, params()))).isEqualTo(RuleStatus.PASSED);
        assertThat(OkvedAllowedRule.matches("10.71", "10.*")).isTrue();
        assertThat(OkvedAllowedRule.matches("100.1", "10.*")).isFalse();
    }

    @Test
    void anomalies_onlyWarnings() {
        PackageFacts round = pkg().doc(Docs.CONTRACT, "date", "2026-03-07", "amount", "1500000.00")
                .doc(Docs.INVOICE, "date", "2026-03-07", "total", "1500000.00")
                .doc(Docs.PAYMENT, "date", "2026-03-07", "amount", "1500000.00").facts();
        assertThat(status(rule(RoundSumsRule.class).evaluate(round, params()))).isEqualTo(RuleStatus.WARNING);
        assertThat(status(rule(WeekendPaymentsRule.class).evaluate(round, params()))).isEqualTo(RuleStatus.WARNING);
        assertThat(status(rule(SameDayChainRule.class).evaluate(round, params()))).isEqualTo(RuleStatus.WARNING);
        assertThat(status(rule(FinBalanceRule.class).evaluate(pkg().doc(Docs.FIN, "assetsTotal", "100.00",
                "liabilitiesTotal", "100.01").facts(), params()))).isEqualTo(RuleStatus.PASSED);
    }
}
