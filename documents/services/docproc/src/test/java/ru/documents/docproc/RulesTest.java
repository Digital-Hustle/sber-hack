package ru.documents.docproc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import ru.documents.docproc.features.processing.model.FieldResult.Check;
import ru.documents.docproc.features.processing.rules.Checksums;
import ru.documents.docproc.features.processing.rules.ConfidenceScorer;
import ru.documents.docproc.features.processing.rules.Dates;
import ru.documents.docproc.features.processing.rules.FieldRules;
import ru.documents.docproc.features.processing.rules.Text;
import ru.documents.docproc.features.processing.rules.WordLocator;

/** FR-24: детерминированные проверки — тест на каждую. */
class RulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Test
    void snilsChecksum() {
        assertThat(Checksums.snils("11223344595")).isTrue();
        assertThat(Checksums.snils("11223344596")).isFalse();
        assertThat(Checksums.snils("123")).isFalse();
    }

    @Test
    void innChecksum() {
        assertThat(Checksums.innPerson("500100732259")).isTrue();
        assertThat(Checksums.innPerson("500100732258")).isFalse();
        assertThat(Checksums.innPerson("7707083893")).isFalse();
    }

    @Test
    void mrzCheckDigit() {
        assertThat(Checksums.mrz("L898902C3")).isEqualTo(6);
        assertThat(Checksums.mrz("740812")).isEqualTo(2);
        assertThat(Checksums.mrz("120415")).isEqualTo(9);
    }

    @Test
    void datesInDifferentFormats() {
        assertThat(Dates.parse("15.01.2020", TODAY)).contains(LocalDate.of(2020, 1, 15));
        assertThat(Dates.parse("15 января 2020 г.", TODAY)).contains(LocalDate.of(2020, 1, 15));
        assertThat(Dates.parse("2020-01-15", TODAY)).contains(LocalDate.of(2020, 1, 15));
        assertThat(Dates.parse("15/01/20", TODAY)).contains(LocalDate.of(2020, 1, 15));
        assertThat(Dates.parse("31.02.2020", TODAY)).isEmpty();
        assertThat(Dates.mrz("740812", TODAY, false)).contains(LocalDate.of(1974, 8, 12));
        assertThat(Dates.mrz("300415", TODAY, true)).contains(LocalDate.of(2030, 4, 15));
    }

    @Test
    void fieldNormalizationAndChecks() {
        var snils = FieldRules.apply("snils", "112 233 445 95", TODAY);
        assertThat(snils.value()).isEqualTo("112-233-445 95");
        assertThat(snils.checks()).containsExactly(new Check("SNILS_CHECKSUM", true));

        var expired = FieldRules.apply("date_expiry", "01.01.2025", TODAY);
        assertThat(expired.value()).isEqualTo("2025-01-01");
        assertThat(expired.checks()).contains(new Check("NOT_EXPIRED", false));

        var dept = FieldRules.apply("dept_code", "770 001", TODAY);
        assertThat(dept.value()).isEqualTo("770-001");

        var series = FieldRules.apply("passport_rf_series", "45 О9", TODAY);
        assertThat(series.value()).isEqualTo("4509");

        var sex = FieldRules.apply("sex", "ЖЕН.", TODAY);
        assertThat(sex.value()).isEqualTo("F");

        assertThat(FieldRules.apply("date_birth", "01.01.2030", TODAY).checks()).contains(new Check("DATE_RANGE", false));
        assertThat(FieldRules.apply("text", "  ", TODAY).value()).isNull();
    }

    @Test
    void transliteration() {
        assertThat(Text.latin("Щукина Юлия")).isEqualTo("SHCHUKINAIULIIA");
        assertThat(Text.key("Ёлкин-Палкин")).isEqualTo("ЕЛКИНПАЛКИН");
    }

    @Test
    void confidenceFormula() {
        ConfidenceScorer s = new ConfidenceScorer(0.4, 0.3, 0.3, 0.7);
        assertThat(s.score(1, 1, List.of(new Check("X", true)))).isEqualTo(1.0);
        assertThat(s.score(1, 1, List.of(new Check("X", false)))).isEqualTo(0.7);
        assertThat(s.score(0.9, 0.9, List.of())).isEqualTo(0.84);
    }

    @Test
    void locatesRawAmongOcrWords() throws Exception {
        JsonNode pages = new ObjectMapper().readTree("""
                [{"n":1,"words":[
                  {"t":"СНИЛС","bbox":[10,10,100,40],"conf":0.9},
                  {"t":"112-233-445","bbox":[120,10,300,40],"conf":0.8},
                  {"t":"95","bbox":[310,10,340,40],"conf":0.6}]}]""");
        var loc = WordLocator.find(pages, "112-233-445 95", null).orElseThrow();
        assertThat(loc.page()).isEqualTo(1);
        assertThat(loc.bbox()).containsExactly(120, 10, 340, 40);
        assertThat(loc.ocrConf()).isEqualTo(0.7, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(WordLocator.find(pages, "999", null)).isEmpty();
    }
}
