package com.app.winterarc.ui.auth;

import androidx.annotation.StringRes;

import com.app.winterarc.R;
import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.Credentials;

/** User-facing text for account states. */
final class AuthText {
    private AuthText() {}

    @StringRes
    static int error(AuthError error) {
        switch (error) {
            case INVALID_EMAIL: return R.string.auth_error_invalid_email;
            case EMAIL_IN_USE: return R.string.auth_error_email_in_use;
            case WRONG_CREDENTIALS: return R.string.auth_error_wrong_credentials;
            case WEAK_PASSWORD: return R.string.auth_error_weak_password;
            case ACCOUNT_DISABLED: return R.string.auth_error_disabled;
            case TOO_MANY_REQUESTS: return R.string.auth_error_too_many;
            case NETWORK: return R.string.auth_error_network;
            case EMAIL_NOT_CONFIRMED: return R.string.auth_error_not_confirmed;
            case GOOGLE_NO_ACCOUNT: return R.string.auth_error_google_no_account;
            case NOT_CONFIGURED: return R.string.auth_error_not_configured;
            default: return R.string.auth_error_unknown;
        }
    }

    @StringRes
    static int problem(Credentials.Problem problem) {
        switch (problem) {
            case NAME_EMPTY: return R.string.auth_problem_name_empty;
            case NAME_TOO_LONG: return R.string.auth_problem_name_long;
            case EMAIL_EMPTY: return R.string.auth_problem_email_empty;
            case EMAIL_INVALID: return R.string.auth_problem_email_invalid;
            case PASSWORD_EMPTY: return R.string.auth_problem_password_empty;
            case PASSWORD_TOO_SHORT: return R.string.auth_problem_password_short;
            case PASSWORD_TOO_LONG: return R.string.auth_problem_password_long;
            case PASSWORD_NEEDS_LETTER: return R.string.auth_problem_password_letter;
            case PASSWORD_NEEDS_NUMBER: return R.string.auth_problem_password_number;
            case CONFIRM_EMPTY: return R.string.auth_problem_confirm_empty;
            default: return R.string.auth_problem_passwords_differ;
        }
    }
}
