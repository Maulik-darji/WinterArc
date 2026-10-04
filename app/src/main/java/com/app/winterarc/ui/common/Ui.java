package com.app.winterarc.ui.common;

import android.content.Context;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Small view helpers: edge-to-edge insets, reduced motion, haptics. */
public final class Ui {
    private Ui() {}

    /** True when the user disabled animations (Settings > Accessibility > Remove animations). */
    public static boolean reducedMotion(Context context) {
        float scale = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return scale == 0f;
    }

    /** Pads {@code view} by the system bar insets on the requested edges, keeping its own padding. */
    public static void applySystemBarPadding(View view, boolean top, boolean bottom) {
        int left = view.getPaddingLeft();
        int t = view.getPaddingTop();
        int right = view.getPaddingRight();
        int b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(left + bars.left, t + (top ? bars.top : 0), right + bars.right,
                    b + (bottom ? Math.max(bars.bottom, ime.bottom) : 0));
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Adds the bottom system bar inset to a view's bottom margin (e.g. a floating button). */
    public static void applyBottomInsetMargin(View view) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int base = lp.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.bottomMargin = base + bars.bottom;
            v.setLayoutParams(p);
            return insets;
        });
    }

    public static void confirmHaptic(View view, boolean enabled) {
        if (!enabled) return;
        view.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
