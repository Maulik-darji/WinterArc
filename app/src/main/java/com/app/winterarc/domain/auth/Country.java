package com.app.winterarc.domain.auth;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A dialling region with the national-number length range accepted for mobile numbers. This is a
 * light, offline sanity check only; the SMS provider performs the authoritative validation.
 */
public record Country(String iso, String dialCode, int minDigits, int maxDigits) {

    /** Common regions first; others fall back to the generic E.164 rule via {@link #generic}. */
    public static final List<Country> ALL = Collections.unmodifiableList(Arrays.asList(
            new Country("IN", "91", 10, 10),
            new Country("US", "1", 10, 10),
            new Country("CA", "1", 10, 10),
            new Country("GB", "44", 10, 10),
            new Country("AU", "61", 9, 9),
            new Country("AE", "971", 9, 9),
            new Country("SG", "65", 8, 8),
            new Country("DE", "49", 10, 11),
            new Country("FR", "33", 9, 9),
            new Country("ES", "34", 9, 9),
            new Country("IT", "39", 9, 10),
            new Country("NL", "31", 9, 9),
            new Country("IE", "353", 9, 9),
            new Country("NZ", "64", 8, 10),
            new Country("ZA", "27", 9, 9),
            new Country("NG", "234", 10, 10),
            new Country("KE", "254", 9, 9),
            new Country("BR", "55", 10, 11),
            new Country("MX", "52", 10, 10),
            new Country("JP", "81", 10, 10),
            new Country("SA", "966", 9, 9),
            new Country("PK", "92", 10, 10),
            new Country("BD", "880", 10, 10),
            new Country("LK", "94", 9, 9),
            new Country("NP", "977", 10, 10)));

    public static Country generic(String iso, String dialCode) {
        return new Country(iso, dialCode, 6, 14);
    }

    /** The country for an ISO code, or India as the default region. */
    public static Country forIso(@Nullable String iso) {
        if (iso != null) {
            String upper = iso.toUpperCase(Locale.ROOT);
            for (Country c : ALL) if (c.iso.equals(upper)) return c;
        }
        return ALL.get(0);
    }

    /** "+91" */
    public String prefix() {
        return "+" + dialCode;
    }

    /** Localised region name, e.g. "India". */
    public String displayName(Locale locale) {
        return new Locale("", iso).getDisplayCountry(locale);
    }
}
