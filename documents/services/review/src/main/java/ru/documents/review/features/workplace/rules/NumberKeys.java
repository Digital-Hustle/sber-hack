package ru.documents.review.features.workplace.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ключи номеров документов для поиска того же номера в других заявках (FR-03): {@code вид:поле:номер}.
 * Номер нормализуется (только буквы и цифры, верхний регистр); серия и номер склеиваются.
 */
public final class NumberKeys {

    /** Поля-номера, по которым ищем совпадения. {@code number} берётся вместе с {@code series}. */
    public static final List<String> FIELDS = List.of("documentNumber", "passportNumber", "snils", "inn", "number");

    private static final int MIN_LENGTH = 6;

    private NumberKeys() {
    }

    /**
     * Ключи документов заявки.
     *
     * @param docs документы с итоговыми значениями
     * @return ключ → документ (первый с таким ключом)
     */
    public static Map<String, DocFacts> of(List<DocFacts> docs) {
        Map<String, DocFacts> out = new LinkedHashMap<>();
        for (DocFacts d : docs) {
            if (d.type() == null) {
                continue;
            }
            for (String f : FIELDS) {
                String v = "number".equals(f) ? join(d.get("series"), d.get("number")) : d.get(f);
                String n = normalize(v);
                if (n != null) {
                    out.putIfAbsent(key(d.type(), f, n), d);
                }
            }
        }
        return out;
    }

    /**
     * Ключ.
     *
     * @param type  вид документа
     * @param field поле
     * @param norm  нормализованный номер
     * @return ключ
     */
    public static String key(String type, String field, String norm) {
        return type + ":" + field + ":" + norm;
    }

    /**
     * Нормализованный номер или null, если он слишком короткий, чтобы сравнивать.
     *
     * @param v значение
     * @return номер
     */
    public static String normalize(String v) {
        if (v == null) {
            return null;
        }
        String n = v.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
        return n.length() < MIN_LENGTH ? null : n;
    }

    private static String join(String series, String number) {
        if (number == null) {
            return null;
        }
        return (series == null ? "" : series) + number;
    }
}
