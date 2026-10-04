package com.app.winterarc.domain.auth;

import androidx.annotation.Nullable;

/**
 * A validated mobile number.
 *
 * @param e164     "+919876543210", the format the SMS provider expects
 * @param national digits only, without the country code or trunk prefix
 */
public record PhoneNumber(Country country, String national, String e164) {

    public enum Problem { EMPTY, INVALID_CHARACTERS, TOO_SHORT, TOO_LONG }

    /** Either a number or the reason it was rejected. */
    public record Result(@Nullable PhoneNumber number, @Nullable Problem problem) {
        public boolean isValid() {
            return number != null;
        }
    }

    /**
     * Normalises user input: spaces, dashes, dots and brackets are ignored, a leading trunk "0" is
     * dropped, and a pasted "+&lt;dial code&gt;" prefix for the selected country is accepted.
     */
    public static Result parse(Country country, @Nullable String input) {
        if (input == null || input.trim().isEmpty()) return new Result(null, Problem.EMPTY);
        String raw = input.trim().replaceAll("[\\s\\-().]", "");
        if (raw.startsWith("+")) {
            if (!raw.startsWith(country.prefix())) return new Result(null, Problem.INVALID_CHARACTERS);
            raw = raw.substring(country.prefix().length());
        } else if (raw.startsWith("00" + country.dialCode())) {
            raw = raw.substring(2 + country.dialCode().length());
        }
        if (!raw.matches("\\d+")) return new Result(null, Problem.INVALID_CHARACTERS);
        // A single leading trunk zero (e.g. 07700 in the UK) is not part of the international number.
        if (raw.startsWith("0") && raw.length() > country.minDigits()) raw = raw.substring(1);
        if (raw.length() < country.minDigits()) return new Result(null, Problem.TOO_SHORT);
        if (raw.length() > country.maxDigits()) return new Result(null, Problem.TOO_LONG);
        // E.164 allows at most 15 digits including the country code.
        if (country.dialCode().length() + raw.length() > 15) return new Result(null, Problem.TOO_LONG);
        return new Result(new PhoneNumber(country, raw, country.prefix() + raw), null);
    }

    /** "+91 ••••••3210": safe to show on screen without revealing the whole number. */
    public String masked() {
        int visible = Math.min(4, national.length());
        StringBuilder sb = new StringBuilder(country.prefix()).append(' ');
        for (int i = 0; i < national.length() - visible; i++) sb.append('•');
        return sb.append(national.substring(national.length() - visible)).toString();
    }

    /** "+91 98765 43210"-style grouping for display. */
    public String formatted() {
        StringBuilder sb = new StringBuilder(country.prefix()).append(' ');
        int split = national.length() > 6 ? national.length() / 2 : national.length();
        sb.append(national, 0, split);
        if (split < national.length()) sb.append(' ').append(national.substring(split));
        return sb.toString();
    }
}
