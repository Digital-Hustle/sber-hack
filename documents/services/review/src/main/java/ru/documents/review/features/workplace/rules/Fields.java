package ru.documents.review.features.workplace.rules;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.documents.review.features.workplace.model.DocRow;
import ru.documents.review.features.workplace.model.FieldOverride;

/**
 * Итоговые значения полей: правка оператора ?? значение модели.
 */
public final class Fields {

    private Fields() {
    }

    /**
     * Значения модели из результата docproc.
     *
     * @param result результат
     * @return поле → значение
     */
    public static Map<String, String> model(JsonNode result) {
        Map<String, String> out = new LinkedHashMap<>();
        if (result == null) {
            return out;
        }
        for (JsonNode f : result.path("fields")) {
            JsonNode v = f.get("value");
            out.put(f.path("name").asText(), v == null || v.isNull() ? null : v.asText());
        }
        return out;
    }

    /**
     * Итог по документу.
     *
     * @param d         документ
     * @param overrides правки
     * @return факты
     */
    public static DocFacts facts(DocRow d, List<FieldOverride> overrides) {
        Map<String, String> values = model(d.result());
        for (FieldOverride o : overrides) {
            values.put(o.name(), o.value());
        }
        String type = d.type() != null && !"UNKNOWN".equals(d.type()) ? d.type() : null;
        String name = d.result() != null && d.result().hasNonNull("typeName") ? d.result().get("typeName").asText()
                : DocTypes.name(type);
        return new DocFacts(d.id(), type, name, values);
    }
}
