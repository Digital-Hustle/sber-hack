package ru.proxodnaya.people.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.people.shared.Cursor;

class CursorTest {

    @Test
    void encodeDecode_roundTrip() {
        // given
        UUID id = UUID.randomUUID();

        // when
        UUID decoded = Cursor.decode(Cursor.encode(id));

        // then
        assertThat(decoded).isEqualTo(id);
    }

    @Test
    void decode_emptyMeansFirstPage() {
        assertThat(Cursor.decode(null)).isNull();
        assertThat(Cursor.decode(" ")).isNull();
    }

    @Test
    void decode_garbage_isValidationError() {
        assertThatThrownBy(() -> Cursor.decode("@@not-base64@@")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Cursor.decode("bm90LWEtdXVpZA")).isInstanceOf(DomainException.class);
    }
}
