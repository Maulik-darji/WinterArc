package com.app.winterarc.domain.model;

/**
 * Enums are persisted by a stable string key, never by ordinal or constant name, so renaming a
 * Java constant can never corrupt stored data.
 */
public final class StableKeys {
    private StableKeys() {}

    public interface Keyed {
        String key();
    }

    public static <T extends Enum<T> & Keyed> T fromKey(Class<T> type, String key, T fallback) {
        if (key == null) return fallback;
        for (T value : type.getEnumConstants()) {
            if (value.key().equals(key)) return value;
        }
        return fallback;
    }
}
