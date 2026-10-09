package ru.documents.docproc.features.processing.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Разметка страниц многостраничного файла по документам (FR-07, DP-14).
 * Модель предлагает {@code segments: [{type, pages}]}, код проверяет и решает: страница принадлежит первому
 * сегменту, который её назвал; страницы без сегмента и с UNKNOWN остаются основному документу
 * (это может быть оборот или страница прописки). Основной документ — тот, что выбрала классификация.
 */
public final class PageSegments {

    private static final String UNKNOWN = "UNKNOWN";

    private PageSegments() {
    }

    /**
     * Итог разметки.
     *
     * @param primaryPages страницы основного документа (номера n из OCR, по возрастанию)
     * @param others       другие найденные виды → их страницы (в порядке первой страницы)
     */
    public record Split(List<Integer> primaryPages, Map<String, List<Integer>> others) {

        /** В файле больше одного документа. */
        public boolean multi() {
            return !others.isEmpty();
        }
    }

    /**
     * Разобрать ответ модели.
     *
     * @param segments  массив сегментов из ответа модели (может отсутствовать)
     * @param primary   вид документа по классификации
     * @param pages     номера страниц файла
     * @param known     допустимые коды видов
     * @return разметка; при противоречивом ответе — все страницы основному документу
     */
    public static Split split(JsonNode segments, String primary, List<Integer> pages, Set<String> known) {
        Map<Integer, String> owner = new LinkedHashMap<>();
        if (segments != null && segments.isArray()) {
            for (JsonNode s : segments) {
                String type = s.path("type").asText(UNKNOWN);
                if (!known.contains(type)) {
                    type = UNKNOWN;
                }
                for (JsonNode p : s.path("pages")) {
                    if (p.canConvertToInt() && pages.contains(p.asInt())) {
                        owner.putIfAbsent(p.asInt(), type);
                    }
                }
            }
        }
        boolean primarySeen = owner.containsValue(primary);
        Map<String, List<Integer>> others = new LinkedHashMap<>();
        List<Integer> mine = new ArrayList<>();
        for (Integer n : new TreeSet<>(pages)) {
            String t = owner.getOrDefault(n, UNKNOWN);
            if (primarySeen && !t.equals(primary) && !UNKNOWN.equals(t)) {
                others.computeIfAbsent(t, k -> new ArrayList<>()).add(n);
            } else {
                mine.add(n);
            }
        }
        if (mine.isEmpty()) {
            return new Split(new ArrayList<>(new TreeSet<>(pages)), Map.of());
        }
        return new Split(mine, others);
    }

    /**
     * «1–2, 4» для текста замечания.
     *
     * @param pages номера по возрастанию
     * @return диапазоны
     */
    public static String ranges(List<Integer> pages) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < pages.size()) {
            int j = i;
            while (j + 1 < pages.size() && pages.get(j + 1) == pages.get(j) + 1) {
                j++;
            }
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(pages.get(i));
            if (j > i) {
                b.append('–').append(pages.get(j));
            }
            i = j + 1;
        }
        return b.toString();
    }
}
