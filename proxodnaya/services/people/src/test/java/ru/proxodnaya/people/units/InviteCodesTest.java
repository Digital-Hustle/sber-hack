package ru.proxodnaya.people.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.people.config.properties.PeopleProperties;
import ru.proxodnaya.people.features.device.command.service.InviteCodes;

class InviteCodesTest {

    private static InviteCodes codes(String pepper) {
        return new InviteCodes(new PeopleProperties(new PeopleProperties.Invite(Duration.ofHours(1), pepper, "https://x")));
    }

    @Test
    void generate_sixCharsFromUnambiguousAlphabet() {
        // given
        InviteCodes codes = codes("p".repeat(32));
        Set<String> seen = new HashSet<>();

        // when
        for (int idx = 0; idx < 1000; idx++) {
            seen.add(codes.generate());
        }

        // then
        assertThat(seen).hasSizeGreaterThan(990).allMatch(code -> code.matches("^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$"));
    }

    @Test
    void hash_ignoresCaseAndSpaces_dependsOnPepper() {
        // given
        InviteCodes first = codes("a".repeat(32));
        InviteCodes second = codes("b".repeat(32));

        // when / then
        assertThat(first.hash(" ab3k9z ")).isEqualTo(first.hash("AB3K9Z"));
        assertThat(first.hash("AB3K9Z")).isNotEqualTo(second.hash("AB3K9Z")).hasSize(64);
    }
}
