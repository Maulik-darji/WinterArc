package com.app.winterarc.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AmountTest {
    @Test
    public void parsesDecimalsExactly() {
        assertEquals(new Amount(21_100), Amount.parse("21.1"));
        assertEquals(new Amount(42_195), Amount.parse("42.195"));
        assertEquals(new Amount(500), Amount.parse("0,5"));
        assertEquals(Amount.whole(5), Amount.parse(" 5 "));
    }

    @Test
    public void rejectsInvalidInput() {
        assertNull(Amount.parse(""));
        assertNull(Amount.parse("abc"));
        assertNull(Amount.parse("-1"));
        assertNull(Amount.parse("1.2345")); // more precision than we store
    }

    @Test
    public void decimalSumsHaveNoFloatingPointDrift() {
        // 0.1 + 0.2 is famously 0.30000000000000004 in doubles; amounts stay exact.
        Amount sum = Amount.parse("0.1").plus(Amount.parse("0.2"));
        assertEquals(Amount.parse("0.3"), sum);
        assertTrue(sum.atLeast(Amount.parse("0.3")));
    }

    @Test
    public void pickerColumnsRoundTrip() {
        Amount a = Amount.of(21, 1);
        assertEquals(21, a.wholePart());
        assertEquals(1, a.tenthsPart());
        assertEquals("21.1", a.toString());
    }
}
