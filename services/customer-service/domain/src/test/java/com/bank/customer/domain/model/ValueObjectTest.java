package com.bank.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bank.customer.domain.exception.InvalidValueException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Value objects: what they accept, what they refuse, and what they normalise.
 *
 * <p>Each one is validated once, at construction. Everything downstream that
 * accepts the type can then stop re-checking — which is the point. The
 * alternative is the same guard clause copy-pasted into fifteen call sites, one
 * of which is missing it.
 */
class ValueObjectTest {

    @Nested
    @DisplayName("Identification")
    class IdentificationTest {

        @Test
        @DisplayName("normalises to upper case so uniqueness actually works")
        void normalisesCase() {
            // Without this, "a-123" and "A-123" both fit through a case-sensitive
            // unique index and the same person exists twice.
            assertThat(new Identification("ab-123").value()).isEqualTo("AB-123");
        }

        @Test
        @DisplayName("strips surrounding whitespace")
        void stripsWhitespace() {
            assertThat(new Identification("  1712345678  ").value()).isEqualTo("1712345678");
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {"", "   ", "1234", "1712345678901234567890X", "17123 4567", "1712#4567"})
        @DisplayName("rejects blank, too short, too long, and illegal characters")
        void rejectsInvalid(String raw) {
            assertThatThrownBy(() -> new Identification(raw))
                    .isInstanceOf(InvalidValueException.class)
                    .satisfies(e -> assertThat(((InvalidValueException) e).field()).isEqualTo("identification"));
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> new Identification(null)).isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("PhoneNumber")
    class PhoneNumberTest {

        @ParameterizedTest(name = "\"{0}\" is stored as digits only")
        @ValueSource(strings = {"098 254 785", "(098)254-785", "098-254-785", "098254785"})
        @DisplayName("strips formatting, because formatting is presentation")
        void stripsSeparators(String raw) {
            // All four are the same telephone number. Storing them as four
            // different strings makes them impossible to compare or deduplicate.
            assertThat(new PhoneNumber(raw).value()).isEqualTo("098254785");
        }

        @Test
        @DisplayName("keeps a leading plus for international numbers")
        void keepsInternationalPrefix() {
            assertThat(new PhoneNumber("+593 98 254 785").value()).isEqualTo("+59398254785");
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {"", "   ", "12345", "098-ABC-785", "098/254/785"})
        @DisplayName("rejects blank, too few digits, and letters")
        void rejectsInvalid(String raw) {
            assertThatThrownBy(() -> new PhoneNumber(raw)).isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("PersonName")
    class PersonNameTest {

        @Test
        @DisplayName("strips surrounding whitespace")
        void stripsWhitespace() {
            assertThat(new PersonName("  Jose Lema  ").value()).isEqualTo("Jose Lema");
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {"", "   ", "J"})
        @DisplayName("rejects blank and single characters")
        void rejectsInvalid(String raw) {
            assertThatThrownBy(() -> new PersonName(raw)).isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("rejects a name beyond the maximum length")
        void rejectsTooLong() {
            assertThatThrownBy(() -> new PersonName("x".repeat(121))).isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("PlainPassword")
    class PlainPasswordTest {

        @Test
        @DisplayName("accepts the shortest legal password")
        void acceptsMinimum() {
            assertThatCode(() -> new PlainPassword("1234")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects anything shorter")
        void rejectsTooShort() {
            assertThatThrownBy(() -> new PlainPassword("123")).isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("rejects input beyond 72 bytes, which BCrypt would silently truncate")
        void rejectsBeyondBcryptCeiling() {
            // Accepting it would mean telling the customer their 100-character
            // password was honoured when only the first 72 bytes were used.
            assertThatThrownBy(() -> new PlainPassword("a".repeat(73))).isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("counts bytes, not characters, at the 72 limit")
        void countsBytesNotChars() {
            // 40 three-byte characters are 120 bytes. A char-based check would
            // wave this through and BCrypt would quietly cut it in half.
            assertThatThrownBy(() -> new PlainPassword("€".repeat(40))).isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("masks its value in toString")
        void masksValue() {
            // A record's generated toString prints every component. Without this
            // override, the password reaches any log line that touches it.
            assertThat(new PlainPassword("s3cret").toString()).isEqualTo("PlainPassword[****]");
        }
    }

    @Nested
    @DisplayName("CustomerId")
    class CustomerIdTest {

        @Test
        @DisplayName("parses a valid UUID")
        void parsesUuid() {
            String raw = "3f1a8d2e-7c44-4b0a-9c6e-1f2b3c4d5e6f";
            assertThat(CustomerId.of(raw).value()).isEqualTo(UUID.fromString(raw));
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {"", "not-a-uuid", "3f1a8d2e-7c44-4b0a-9c6e"})
        @DisplayName("turns a malformed id into a domain validation failure")
        void rejectsMalformed(String raw) {
            // Not an IllegalArgumentException escaping from UUID.fromString. The
            // boundary needs a typed failure it can map to a 400.
            assertThatThrownBy(() -> CustomerId.of(raw))
                    .isInstanceOf(InvalidValueException.class)
                    .satisfies(e -> assertThat(((InvalidValueException) e).field()).isEqualTo("customerId"));
        }
    }

    @Nested
    @DisplayName("Address")
    class AddressTest {

        @Test
        @DisplayName("strips surrounding whitespace")
        void stripsWhitespace() {
            assertThat(new Address("  Amazonas y NNUU  ").value()).isEqualTo("Amazonas y NNUU");
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {"", "  ", "ab"})
        @DisplayName("rejects blank and too-short addresses")
        void rejectsInvalid(String raw) {
            assertThatThrownBy(() -> new Address(raw)).isInstanceOf(InvalidValueException.class);
        }
    }
}
