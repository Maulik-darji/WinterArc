package com.app.winterarc.domain.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;

public class CredentialsTest {
    @Test
    public void validSignUpHasNoProblems() {
        assertTrue(Credentials.validateSignUp("Maulik", " Maulik@Example.com ", "winter2026", "winter2026").isEmpty());
        assertEquals("maulik@example.com", Credentials.normalizeEmail(" Maulik@Example.com "));
    }

    @Test
    public void passwordRules() {
        assertEquals(EnumSet.of(Credentials.Problem.PASSWORD_TOO_SHORT), Credentials.validateNewPassword("abc12"));
        assertEquals(EnumSet.of(Credentials.Problem.PASSWORD_NEEDS_NUMBER), Credentials.validateNewPassword("abcdefgh"));
        assertEquals(EnumSet.of(Credentials.Problem.PASSWORD_NEEDS_LETTER), Credentials.validateNewPassword("12345678"));
        assertEquals(EnumSet.of(Credentials.Problem.PASSWORD_EMPTY), Credentials.validateNewPassword(""));
        assertTrue(Credentials.validateNewPassword("pässwört1").isEmpty()); // non-ASCII letters count
    }

    @Test
    public void confirmMustMatch() {
        assertTrue(Credentials.validateSignUp("A", "a@b.co", "winter2026", "winter2027")
                .contains(Credentials.Problem.PASSWORDS_DIFFER));
        assertTrue(Credentials.validateSignUp("A", "a@b.co", "winter2026", "")
                .contains(Credentials.Problem.CONFIRM_EMPTY));
    }

    @Test
    public void emailAndNameRules() {
        assertEquals(EnumSet.of(Credentials.Problem.EMAIL_INVALID), Credentials.validateEmail("not-an-email"));
        assertEquals(EnumSet.of(Credentials.Problem.EMAIL_INVALID), Credentials.validateEmail("a@b"));
        assertEquals(EnumSet.of(Credentials.Problem.EMAIL_EMPTY), Credentials.validateEmail("  "));
        assertTrue(Credentials.validateSignUp(" ", "a@b.co", "winter2026", "winter2026").contains(Credentials.Problem.NAME_EMPTY));
        assertTrue(Credentials.validateLogIn("a@b.co", "").contains(Credentials.Problem.PASSWORD_EMPTY));
    }

    @Test
    public void handlesFromAccount() {
        assertEquals("@mauliksingh", com.app.winterarc.domain.social.SocialHandles.fromAccount("maulik.singh@x.com", "M"));
        assertEquals("@member", com.app.winterarc.domain.social.SocialHandles.fromAccount(null, "  "));
    }
}
