package ru.documents.intake.features.applications.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Набор документов по ответам анкеты (FR-10): правила — строки doc_requirements (данные, ADR-010).
 * Условие выполнено, если каждый ключ condition совпадает с ответом (значение или одно из списка).
 */
@Component
public class RequirementsEngine {

    /**
     * Правило из БД.
     *
     * @param type      вид документа
     * @param title     название
     * @param hint      пояснение
     * @param condition условие
     * @param required  обязателен
     */
    public record Rule(String type, String title, String hint, JsonNode condition, boolean required) {
    }

    /**
     * Подобрать набор.
     *
     * @param rules   правила по порядку
     * @param answers ответы
     * @return пункты без повторов по виду (первое совпавшее правило)
     */
    public List<Rule> select(List<Rule> rules, Map<String, Object> answers) {
        Map<String, Rule> out = new LinkedHashMap<>();
        for (Rule r : rules) {
            if (matches(r.condition(), answers)) {
                out.putIfAbsent(r.type(), r);
            }
        }
        return new ArrayList<>(out.values());
    }

    static boolean matches(JsonNode cond, Map<String, Object> answers) {
        var it = cond.fields();
        while (it.hasNext()) {
            var e = it.next();
            Object a = answers.get(e.getKey());
            if (a == null) {
                return false;
            }
            JsonNode want = e.getValue();
            boolean ok;
            if (want.isArray()) {
                ok = false;
                for (JsonNode w : want) {
                    ok |= w.asText().equals(String.valueOf(a));
                }
            } else {
                ok = want.asText().equals(String.valueOf(a));
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
