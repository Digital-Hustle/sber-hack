package ru.documents.docproc.features.processing.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Разметка страниц многостраничного файла (DP-14). */
class PageSegmentsTest {

    private static final Set<String> KNOWN = Set.of("PASSPORT_RF", "SNILS", "INN", "UNKNOWN");
    private final ObjectMapper om = new ObjectMapper();

    private JsonNode j(String s) throws Exception {
        return om.readTree(s);
    }

    @Test
    void twoDocumentsInOneFile() throws Exception {
        var s = PageSegments.split(j("[{\"type\":\"PASSPORT_RF\",\"pages\":[1,2]},{\"type\":\"SNILS\",\"pages\":[3]}]"),
                "PASSPORT_RF", List.of(1, 2, 3), KNOWN);
        assertThat(s.multi()).isTrue();
        assertThat(s.primaryPages()).containsExactly(1, 2);
        assertThat(s.others()).isEqualTo(Map.of("SNILS", List.of(3)));
    }

    @Test
    void unknownAndUnclaimedPagesStayWithPrimary() throws Exception {
        var s = PageSegments.split(j("[{\"type\":\"SNILS\",\"pages\":[1]},{\"type\":\"UNKNOWN\",\"pages\":[2]}]"),
                "SNILS", List.of(1, 2, 3), KNOWN);
        assertThat(s.multi()).isFalse();
        assertThat(s.primaryPages()).containsExactly(1, 2, 3);
    }

    @Test
    void inconsistentAnswerKeepsWholeFile() throws Exception {
        // модель не назвала основной вид ни на одной странице, выдумала вид и страницу 9 — не доверяем разметке
        var s = PageSegments.split(j("[{\"type\":\"INN\",\"pages\":[1,2]},{\"type\":\"DIPLOMA\",\"pages\":[9]}]"),
                "SNILS", List.of(1, 2), KNOWN);
        assertThat(s.multi()).isFalse();
        assertThat(s.primaryPages()).containsExactly(1, 2);
        assertThat(PageSegments.split(null, "SNILS", List.of(1, 2), KNOWN).primaryPages()).containsExactly(1, 2);
    }

    @Test
    void pageClaimedTwiceBelongsToFirstSegment() throws Exception {
        var s = PageSegments.split(j("[{\"type\":\"INN\",\"pages\":[1,2]},{\"type\":\"SNILS\",\"pages\":[2,3]}]"),
                "INN", List.of(1, 2, 3), KNOWN);
        assertThat(s.primaryPages()).containsExactly(1, 2);
        assertThat(s.others()).isEqualTo(Map.of("SNILS", List.of(3)));
    }

    @Test
    void ranges() {
        assertThat(PageSegments.ranges(List.of(1, 2, 3, 5, 7, 8))).isEqualTo("1–3, 5, 7–8");
        assertThat(PageSegments.ranges(List.of(4))).isEqualTo("4");
    }
}
