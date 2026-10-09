package ru.proxodnaya.assistant.features.assistant.validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * FR-43: числа ответа ⊂ числа инструментов (с округлением и процентами); даты, время, id и пункты списка не проверяются.
 */
class NumberValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void numbersFromData_roundingAndPercent_accepted() throws Exception {
        // given
        JsonNode data = JSON.readTree("{\"items\":[{\"workerId\":\"w42\",\"workedHours\":12.5,\"late\":3}],"
                + "\"deniedShare\":0.1234,\"total\":1234}");
        NumberValidator validator = new NumberValidator().allow(data);

        // when / then
        assertThat(validator.unknown("w42 отработал 12,5 ч, опозданий — 3; отказов 12,3 % (0,12), всего 1 234 прохода")).isEmpty();
        assertThat(validator.unknown("1. w42 — 12.5 ч\n2. Данные на 2026-10-09 14:30")).isEmpty();
        assertThat(validator.unknown("людей в списке: 1")).isEmpty();
    }

    @Test
    void inventedNumbers_reported() throws Exception {
        // given
        NumberValidator validator = new NumberValidator().allow(JSON.readTree("{\"workedHours\":12.4,\"inside\":7}"))
                .allowText("Сколько часов за 5 дней?");

        // when / then
        assertThat(validator.unknown("За 5 дней — 13 ч, на объекте 7, рост на 40%")).containsExactly("13", "40%");
        assertThat(validator.unknown("в 3 120 зонах")).containsExactly("3 120");
    }
}
