package com.app.winterarc.ui.common;

import androidx.appcompat.app.AppCompatDelegate;

import com.app.winterarc.domain.repository.ThemeMode;

public final class ThemeController {
    private ThemeController() {}

    public static void apply(ThemeMode mode) {
        int night;
        switch (mode) {
            case LIGHT: night = AppCompatDelegate.MODE_NIGHT_NO; break;
            case DARK: night = AppCompatDelegate.MODE_NIGHT_YES; break;
            default: night = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
        }
        if (AppCompatDelegate.getDefaultNightMode() != night) AppCompatDelegate.setDefaultNightMode(night);
    }
}
