package com.app.winterarc.domain.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneNumberTest {
    private static final Country IN = Country.forIso("IN");
    private static final Country GB = Country.forIso("GB");
    private static final Country US = Country.forIso("us");

    @Test
    public void normalisesFormattingCharacters() {
        PhoneNumber n = PhoneNumber.parse(IN, " 98765-43210 ").number();
        assertEquals("+919876543210", n.e164());
        assertEquals("+14155550123", PhoneNumber.parse(US, "(415) 555.0123").number().e164());
    }

    @Test
    public void acceptsPastedInternationalFormForSelectedCountry() {
        assertEquals("+919876543210", PhoneNumber.parse(IN, "+91 98765 43210").number().e164());
        assertEquals("+919876543210", PhoneNumber.parse(IN, "0091 9876543210").number().e164());
    }

    @Test
    public void dropsTrunkZero() {
        assertEquals("+447700900123", PhoneNumber.parse(GB, "07700 900123").number().e164());
    }

    @Test
    public void rejectsBadInput() {
        assertEquals(PhoneNumber.Problem.EMPTY, PhoneNumber.parse(IN, "  ").problem());
        assertEquals(PhoneNumber.Problem.INVALID_CHARACTERS, PhoneNumber.parse(IN, "98765abc10").problem());
        assertEquals(PhoneNumber.Problem.INVALID_CHARACTERS, PhoneNumber.parse(IN, "+44 7700 900123").problem());
        assertEquals(PhoneNumber.Problem.TOO_SHORT, PhoneNumber.parse(IN, "98765").problem());
        assertEquals(PhoneNumber.Problem.TOO_LONG, PhoneNumber.parse(IN, "987654321012").problem());
        assertFalse(PhoneNumber.parse(IN, null).isValid());
    }

    @Test
    public void unknownRegionDefaultsToIndia() {
        assertEquals("IN", Country.forIso("ZZ").iso());
        assertEquals("IN", Country.forIso(null).iso());
        assertTrue(Country.ALL.size() > 10);
    }

    @Test
    public void maskingShowsOnlyLastFourDigits() {
        PhoneNumber n = PhoneNumber.parse(IN, "9876543210").number();
        assertEquals("+91 ••••••3210", n.masked());
        assertEquals("+91 98765 43210", n.formatted());
    }
}
