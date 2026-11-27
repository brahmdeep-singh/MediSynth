package com.personal.medisynth.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void acceptsLongPasswordsWithLettersAndDigits() {
        assertThat(validator.isValid("Str0ngPassw0rd!", null)).isTrue();
        assertThat(validator.isValid("abcdefghi1", null)).isTrue();
    }

    @Test
    void rejectsWeakPasswords() {
        assertThat(validator.isValid("short1", null)).isFalse();
        assertThat(validator.isValid("onlylettersherexx", null)).isFalse();
        assertThat(validator.isValid("1234567890123", null)).isFalse();
        assertThat(validator.isValid(" Leading1space", null)).isFalse();
    }

    @Test
    void rejectsPasswordsLongerThanBcryptsLimit() {
        assertThat(validator.isValid("a1" + "x".repeat(70), null)).isTrue(); // 72 bytes
        assertThat(validator.isValid("a1" + "x".repeat(71), null)).isFalse(); // 73 bytes
        assertThat(validator.isValid("a1" + "é".repeat(36), null)).isFalse(); // multi-byte characters count as bytes
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
