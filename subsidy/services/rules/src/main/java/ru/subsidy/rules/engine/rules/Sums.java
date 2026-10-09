package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.money.Money;

/**
 * Общие суммы для арифметических правил.
 */
final class Sums {

    private Sums() {
    }

    /**
     * Сумма поля по всем документам вида.
     *
     * @param facts факты
     * @param type вид
     * @param field поле
     * @return сумма или пусто, если ни в одном документе поля нет
     */
    static Optional<BigDecimal> total(PackageFacts facts, String type, String field) {
        List<BigDecimal> values = facts.ofType(type).stream().map(doc -> doc.money(field))
                .flatMap(Optional::stream).toList();
        return values.isEmpty() ? Optional.empty()
                : Optional.of(Money.round(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    /**
     * Подтверждённые затраты: сумма счетов, а если их нет — договоров.
     *
     * @param facts факты
     * @return сумма или пусто
     */
    static Optional<BigDecimal> confirmed(PackageFacts facts) {
        return total(facts, Docs.INVOICE, Docs.TOTAL).or(() -> total(facts, Docs.CONTRACT, Docs.AMOUNT));
    }

    /**
     * Сумма строки таблицы.
     *
     * @param row строка
     * @param key ключ
     * @return сумма или пусто
     */
    static Optional<BigDecimal> money(JsonNode row, String key) {
        return Optional.ofNullable(row.get(key)).filter(node -> !node.isNull()).map(JsonNode::asText)
                .map(Money::parse);
    }

    /**
     * Количество строки таблицы.
     *
     * @param row строка
     * @return количество или пусто
     */
    static Optional<BigDecimal> qty(JsonNode row) {
        JsonNode node = row.get("qty");
        Optional<BigDecimal> result = Optional.empty();
        if (node != null && node.isNumber()) {
            result = Optional.of(node.decimalValue());
        } else if (node != null && node.isTextual()) {
            result = Optional.ofNullable(Money.parse(node.asText()));
        }
        return result;
    }

    /**
     * Сумма позиций документа.
     *
     * @param doc документ
     * @return Σ amount или пусто
     */
    static Optional<BigDecimal> items(DocFacts doc) {
        List<BigDecimal> amounts = doc.rows(Docs.ITEMS).stream().map(row -> money(row, Docs.AMOUNT))
                .flatMap(Optional::stream).toList();
        return amounts.isEmpty() ? Optional.empty()
                : Optional.of(Money.round(amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
}
