package ru.documents.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import ru.documents.common.exception.DomainException;
import ru.documents.intake.features.applications.rules.Questionnaire;
import ru.documents.intake.features.applications.rules.RequirementsEngine;
import ru.documents.intake.features.applications.rules.RequirementsEngine.Rule;

/** FR-10: набор документов зависит от ответов анкеты. */
class RequirementsEngineTest {

    private final ObjectMapper om = new ObjectMapper();
    private final RequirementsEngine engine = new RequirementsEngine();

    private Rule rule(String type, String cond) throws Exception {
        return new Rule(type, type, null, om.readTree(cond), true);
    }

    private List<Rule> rules() throws Exception {
        return List.of(
                rule("PASSPORT_RF", "{\"citizenship\":[\"RF\"]}"),
                rule("MEDICAL", "{\"citizenship\":[\"RF\"],\"medicalRequired\":true}"),
                rule("PASSPORT_FOREIGN", "{\"citizenship\":[\"EAEU\",\"VISA_FREE\"]}"),
                rule("WORK_PATENT", "{\"citizenship\":[\"VISA_FREE\"]}"),
                rule("INN", "{\"citizenship\":[\"RF\"]}"),
                rule("INN", "{\"citizenship\":[\"EAEU\",\"VISA_FREE\"],\"hasInn\":true}"));
    }

    private List<String> types(Map<String, Object> answers) throws Exception {
        return engine.select(rules(), answers).stream().map(Rule::type).toList();
    }

    @Test
    void citizenOfRussia() throws Exception {
        assertThat(types(Map.of("citizenship", "RF", "medicalRequired", false))).containsExactly("PASSPORT_RF", "INN");
        assertThat(types(Map.of("citizenship", "RF", "medicalRequired", true)))
                .containsExactly("PASSPORT_RF", "MEDICAL", "INN");
    }

    @Test
    void visaFreeForeigner() throws Exception {
        assertThat(types(Map.of("citizenship", "VISA_FREE", "hasInn", true)))
                .containsExactly("PASSPORT_FOREIGN", "WORK_PATENT", "INN");
        assertThat(types(Map.of("citizenship", "EAEU", "hasInn", false))).containsExactly("PASSPORT_FOREIGN");
    }

    @Test
    void missingAnswerDoesNotMatch() throws Exception {
        assertThat(types(Map.of())).isEmpty();
    }

    @Test
    void questionnaireRejectsUnknownCitizenship() {
        Questionnaire q = new Questionnaire();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> q.validate(Map.of("citizenship", "MARS")))
                .isInstanceOf(DomainException.class);
    }
}
