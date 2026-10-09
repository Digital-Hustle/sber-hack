package ru.documents.docproc.features.processing.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Находит фрагмент raw среди слов OCR и даёт bbox и уверенность распознавания (docs/LLM.md «Извлечение»).
 */
public final class WordLocator {

    private static final int MAX_WINDOW = 14;

    private WordLocator() {
    }

    /**
     * Где найден фрагмент.
     *
     * @param page    страница с 1
     * @param bbox    [x0, y0, x1, y1]
     * @param ocrConf средняя уверенность слов 0..1
     */
    public record Location(int page, List<Integer> bbox, double ocrConf) {
    }

    /**
     * Найти фрагмент: сначала на указанной странице, затем на остальных.
     *
     * @param pages     страницы ответа ocr
     * @param fragment  raw или value
     * @param hintPage  страница от модели (может быть null)
     * @return место
     */
    public static Optional<Location> find(JsonNode pages, String fragment, Integer hintPage) {
        String target = Text.key(fragment);
        if (target.length() < 2) {
            return Optional.empty();
        }
        List<JsonNode> order = new ArrayList<>();
        for (JsonNode p : pages) {
            if (hintPage != null && p.path("n").asInt() == hintPage) {
                order.add(0, p);
            } else {
                order.add(p);
            }
        }
        for (JsonNode p : order) {
            Optional<Location> hit = onPage(p, target);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    private static Optional<Location> onPage(JsonNode page, String target) {
        JsonNode words = page.path("words");
        int n = words.size();
        List<String> keys = new ArrayList<>(n);
        for (JsonNode w : words) {
            keys.add(Text.key(w.path("t").asText()));
        }
        for (int i = 0; i < n; i++) {
            String k = keys.get(i);
            if (k.isEmpty() || !(target.startsWith(k) || k.contains(target))) {
                continue;
            }
            StringBuilder acc = new StringBuilder();
            for (int j = i; j < n && j < i + MAX_WINDOW; j++) {
                acc.append(keys.get(j));
                String s = acc.toString();
                if (s.contains(target)) {
                    return Optional.of(location(page, words, i, j));
                }
                if (!target.startsWith(s)) {
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private static Location location(JsonNode page, JsonNode words, int from, int to) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = 0;
        int y1 = 0;
        double conf = 0;
        for (int i = from; i <= to; i++) {
            JsonNode b = words.get(i).path("bbox");
            x0 = Math.min(x0, b.get(0).asInt());
            y0 = Math.min(y0, b.get(1).asInt());
            x1 = Math.max(x1, b.get(2).asInt());
            y1 = Math.max(y1, b.get(3).asInt());
            conf += words.get(i).path("conf").asDouble();
        }
        return new Location(page.path("n").asInt(1), List.of(x0, y0, x1, y1), conf / (to - from + 1));
    }
}
