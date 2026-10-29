package com.app.winterarc.domain.auth;

/** Provider-independent sign-in failures, each mapped to a calm, specific message. */
public enum AuthError {
    INVALID_EMAIL,
    EMAIL_IN_USE,
    WRONG_CREDENTIALS,
    WEAK_PASSWORD,
    ACCOUNT_DISABLED,
    TOO_MANY_REQUESTS,
    NETWORK,
    GOOGLE_CANCELLED,
    GOOGLE_NO_ACCOUNT,
    /** The account exists but its email address has not been confirmed yet (log-in). */
    EMAIL_NOT_CONFIRMED,
    /** Sign-up worked, but the account is only active once the emailed link is opened. Not a failure. */
    CONFIRM_EMAIL,
    /** No provider configured (e.g. a release build without Supabase settings). */
    NOT_CONFIGURED,
    UNKNOWN
}
