package com.app.winterarc.ui.auth;

import android.content.Context;

import androidx.annotation.StringRes;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.Country;
import com.app.winterarc.domain.auth.PhoneNumber;

/** User-facing text for sign-up states. */
final class AuthText {
    private AuthText() {}

    @StringRes
    static int error(AuthError error) {
        switch (error) {
            case INVALID_PHONE: return R.string.auth_error_invalid_phone;
            case INVALID_CODE: return R.string.auth_error_invalid_code;
            case CODE_EXPIRED: return R.string.auth_error_code_expired;
            case TOO_MANY_REQUESTS: return R.string.auth_error_too_many;
            case QUOTA_EXCEEDED: return R.string.auth_error_quota;
            case NETWORK: return R.string.auth_error_network;
            case NOT_CONFIGURED: return R.string.auth_error_not_configured;
            default: return R.string.auth_error_unknown;
        }
    }

    static String problem(Context c, PhoneNumber.Problem problem, Country country) {
        String name = country.displayName(Formats.locale(c));
        switch (problem) {
            case EMPTY: return c.getString(R.string.phone_error_empty);
            case INVALID_CHARACTERS: return c.getString(R.string.phone_error_characters);
            case TOO_SHORT: return c.getString(R.string.phone_error_short, name);
            default: return c.getString(R.string.phone_error_long, name);
        }
    }

    /** Regional-indicator flag emoji for an ISO country code, e.g. "IN" → 🇮🇳. */
    static String flag(String iso) {
        int first = Character.codePointAt(iso, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(iso, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    /** "0:24" */
    static String countdown(int seconds) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
