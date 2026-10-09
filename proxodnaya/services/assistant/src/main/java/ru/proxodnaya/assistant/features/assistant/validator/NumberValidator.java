package ru.proxodnaya.assistant.features.assistant.validator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * FR-43: каждое число ответа должно быть в данных инструментов, в вопросе или быть их округлением; доли 0..1 — и в процентах.
 */
public final class NumberValidator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_SCALE = 2;

    private final Set<BigDecimal> allowed = new HashSet<>();

    /**
     * Добавляет числа из JSON: числовые поля и размеры массивов. Строки не дают чисел — в них может быть инструкция (FR-47).
     *
     * @param data данные инструмента
     * @return этот же валидатор
     */
    public NumberValidator allow(JsonNode data) {
        if (data == null) {
            return this;
        }
        if (data.isNumber()) {
            add(data.decimalValue());
        } else if (data.isArray()) {
            add(BigDecimal.valueOf(data.size()));
            data.forEach(this::allow);
        } else if (data.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> fields = data.fields(); fields.hasNext();) {
                allow(fields.next().getValue());
            }
        }
        return this;
    }

    /**
     * Добавляет числа из текста (вопрос пользователя).
     *
     * @param text текст
     * @return этот же валидатор
     */
    public NumberValidator allowText(String text) {
        for (NumberExtractor.Found found : NumberExtractor.extract(text)) {
            add(found.value());
            found.parts().forEach(this::add);
        }
        return this;
    }

    /**
     * Проверяет ответ.
     *
     * @param text ответ модели
     * @return числа, которых нет в данных (пусто — ответ честный)
     */
    public List<String> unknown(String text) {
        List<String> unknown = new ArrayList<>();
        for (NumberExtractor.Found found : NumberExtractor.extract(text)) {
            boolean fine = known(found.value())
                    || found.percent() && known(found.value().divide(HUNDRED))
                    || !found.parts().isEmpty() && found.parts().stream().allMatch(this::known);
            if (!fine) {
                unknown.add(found.raw());
            }
        }
        return unknown;
    }

    private boolean known(BigDecimal value) {
        return allowed.contains(norm(value));
    }

    private void add(BigDecimal value) {
        BigDecimal abs = value.abs();
        allowed.add(norm(abs));
        for (int scale = 0; scale <= MAX_SCALE; scale++) {
            allowed.add(norm(abs.setScale(scale, RoundingMode.HALF_UP)));
        }
        if (abs.compareTo(BigDecimal.ONE) <= 0) {
            BigDecimal percent = abs.multiply(HUNDRED);
            for (int scale = 0; scale <= 1; scale++) {
                allowed.add(norm(percent.setScale(scale, RoundingMode.HALF_UP)));
            }
        }
    }

    private static BigDecimal norm(BigDecimal value) {
        BigDecimal stripped = value.abs().stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
