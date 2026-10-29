package com.app.winterarc.domain.auth;

import androidx.annotation.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure validation for sign-up and log-in forms. */
public final class Credentials {
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_PASSWORD = 128;
    public static final int MAX_NAME = 50;

    // Pragmatic address check: local@domain.tld, no spaces. The provider does the final check.
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private Credentials() {}

    public enum Problem {
        NAME_EMPTY,
        NAME_TOO_LONG,
        EMAIL_EMPTY,
        EMAIL_INVALID,
        PASSWORD_EMPTY,
        PASSWORD_TOO_SHORT,
        PASSWORD_TOO_LONG,
        PASSWORD_NEEDS_LETTER,
        PASSWORD_NEEDS_NUMBER,
        CONFIRM_EMPTY,
        PASSWORDS_DIFFER
    }

    /** Trimmed, lower-cased email (what gets sent to the provider). */
    public static String normalizeEmail(@Nullable String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static Set<Problem> validateSignUp(@Nullable String name, @Nullable String email,
                                              @Nullable String password, @Nullable String confirm) {
        Set<Problem> problems = EnumSet.noneOf(Problem.class);
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) problems.add(Problem.NAME_EMPTY);
        else if (n.length() > MAX_NAME) problems.add(Problem.NAME_TOO_LONG);
        problems.addAll(validateEmail(email));
        problems.addAll(validateNewPassword(password));
        if (confirm == null || confirm.isEmpty()) problems.add(Problem.CONFIRM_EMPTY);
        else if (!confirm.equals(password)) problems.add(Problem.PASSWORDS_DIFFER);
        return problems;
    }

    public static Set<Problem> validateLogIn(@Nullable String email, @Nullable String password) {
        Set<Problem> problems = EnumSet.noneOf(Problem.class);
        problems.addAll(validateEmail(email));
        if (password == null || password.isEmpty()) problems.add(Problem.PASSWORD_EMPTY);
        return problems;
    }

    public static Set<Problem> validateEmail(@Nullable String email) {
        String e = normalizeEmail(email);
        if (e.isEmpty()) return EnumSet.of(Problem.EMAIL_EMPTY);
        if (!EMAIL.matcher(e).matches()) return EnumSet.of(Problem.EMAIL_INVALID);
        return EnumSet.noneOf(Problem.class);
    }

    /** At least 8 characters with one letter and one number. Whitespace is allowed. */
    public static Set<Problem> validateNewPassword(@Nullable String password) {
        Set<Problem> problems = EnumSet.noneOf(Problem.class);
        if (password == null || password.isEmpty()) {
            problems.add(Problem.PASSWORD_EMPTY);
            return problems;
        }
        if (password.length() < MIN_PASSWORD) problems.add(Problem.PASSWORD_TOO_SHORT);
        if (password.length() > MAX_PASSWORD) problems.add(Problem.PASSWORD_TOO_LONG);
        if (!password.matches(".*\\p{L}.*")) problems.add(Problem.PASSWORD_NEEDS_LETTER);
        if (!password.matches(".*\\d.*")) problems.add(Problem.PASSWORD_NEEDS_NUMBER);
        return problems;
    }
}
