package com.app.winterarc.domain.auth;

/** Provider-independent sign-in failures, each mapped to a calm, actionable message. */
public enum AuthError {
    INVALID_PHONE,
    INVALID_CODE,
    CODE_EXPIRED,
    TOO_MANY_REQUESTS,
    QUOTA_EXCEEDED,
    NETWORK,
    /** No SMS provider configured (e.g. a release build without google-services.json). */
    NOT_CONFIGURED,
    UNKNOWN
}
