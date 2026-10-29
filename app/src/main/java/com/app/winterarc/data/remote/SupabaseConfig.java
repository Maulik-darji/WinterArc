package com.app.winterarc.data.remote;

import androidx.annotation.NonNull;

/** Public client configuration embedded in the APK. Data security must be enforced with RLS. */
public record SupabaseConfig(String url, String publishableKey, boolean configured) {
    private static final String UNCONFIGURED_URL = "https://example.invalid/";

    public SupabaseConfig {
        url = normalize(url);
        publishableKey = publishableKey == null ? "" : publishableKey.trim();
        configured = configured && !url.equals(UNCONFIGURED_URL) && !publishableKey.isEmpty();
    }

    @NonNull
    public String retrofitBaseUrl() {
        return configured ? url : UNCONFIGURED_URL;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return UNCONFIGURED_URL;
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }
}
