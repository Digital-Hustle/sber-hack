package ru.documents.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.documents.review.features.workplace.model.Finding;
import ru.documents.review.features.workplace.rules.CrossChecker;
import ru.documents.review.features.workplace.rules.DocFacts;
import ru.documents.review.features.workplace.rules.Masking;
import ru.documents.review.features.workplace.rules.NameMatcher;
import ru.documents.review.features.workplace.rules.NumberKeys;

class RulesTest {

    @Test
    void namesMatchAcrossScriptsAndTransliterationVariants() {
        assertThat(NameMatcher.same("Иванов Иван Иванович", "IVANOV IVAN")).isTrue();
        assertThat(NameMatcher.same("Худойбердиев Шерзод", "KHUDOIBERDIEV SHERZOD")).isTrue();
        assertThat(NameMatcher.same("Худойбердиев Шерзод", "XUDOYBERDIYEV SHERZOD")).isTrue();
        assertThat(NameMatcher.same("Юсупова Зарина", "YUSUPOVA ZARINA")).isTrue();
        assertThat(NameMatcher.same("Жураев Акмал", "DZHURAEV AKMAL")).isTrue();
        assertThat(NameMatcher.same("Каримов Рустам", "KARIMOV RUSTAN")).isTrue();
        assertThat(NameMatcher.same("Каримов Рустам", "NAZAROV RUSTAM")).isFalse();
        assertThat(NameMatcher.same("Ли Ан", "LI AN")).isTrue();
        assertThat(NameMatcher.same("Ли Ан", "LI AM")).isFalse();
    }

    @Test
    void crossCheckFindsMismatchesAndMissing() {
        UUID p = UUID.randomUUID();
        UUID w = UUID.randomUUID();
        DocFacts passport = new DocFacts(p, "PASSPORT_FOREIGN", "Паспорт", Map.of("surname", "IVANOV",
                "givenNames", "IVAN", "birthDate", "1990-01-02", "documentNumber", "AB1234567"));
        DocFacts patent = new DocFacts(w, "WORK_PATENT", "Патент", Map.of("fullName", "Иванов Иван",
                "birthDate", "1990-01-03", "passportNumber", "1234567"));
        List<Finding> f = CrossChecker.check(List.of(passport, patent), List.of("PASSPORT_FOREIGN", "MEDICAL"));
        assertThat(f).extracting(Finding::code, Finding::field).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("MISSING_DOCUMENT", null),
                org.assertj.core.groups.Tuple.tuple("CROSS_MISMATCH", "birthDate"));
        assertThat(CrossChecker.applicantName(List.of(patent, passport))).isEqualTo("IVANOV IVAN");
    }

    @Test
    void masking() {
        assertThat(Masking.number("AB1234567")).isEqualTo("•••••4567");
        assertThat(Masking.number("123-456-789 01")).isEqualTo("•••-•••-•89 01");
        assertThat(Masking.text("Иванов Иван")).isEqualTo("И••• И•••");
        assertThat(Masking.contact("user@mail.ru")).isEqualTo("u•••@mail.ru");
    }

    @Test
    void numberKeysNormalizeAndJoinSeries() {
        DocFacts rf = new DocFacts(UUID.randomUUID(), "PASSPORT_RF", "Паспорт РФ",
                Map.of("series", "45 06", "number", "123456"));
        DocFacts foreign = new DocFacts(UUID.randomUUID(), "PASSPORT_FOREIGN", "Паспорт",
                Map.of("documentNumber", "ab-1234567"));
        DocFacts shortNo = new DocFacts(UUID.randomUUID(), "INN", "ИНН", Map.of("inn", "123"));
        DocFacts unknown = new DocFacts(UUID.randomUUID(), null, null, Map.of("documentNumber", "AB1234567"));

        Map<String, DocFacts> keys = NumberKeys.of(List.of(rf, foreign, shortNo, unknown));

        assertThat(keys).containsOnlyKeys("PASSPORT_RF:number:4506123456", "PASSPORT_FOREIGN:documentNumber:AB1234567");
        assertThat(NumberKeys.normalize("АБ 12-34 56")).isEqualTo("АБ123456");
        assertThat(NumberKeys.normalize("12 34")).isNull();
    }
}
