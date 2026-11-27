package com.personal.medisynth.service;

import java.util.Locale;

/** Central input clean-up so uniqueness checks and DB constraints see the same values. */
final class InputNormalizer {

    private InputNormalizer() {
    }

    static String email(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    static String text(String value) {
        return value == null ? null : value.trim();
    }

    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    static String license(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
