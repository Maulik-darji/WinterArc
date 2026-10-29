package com.app.winterarc.ui.common;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Semantic haptic feedback. Screens say what happened (a selection, a toggle, a success, a
 * rejection, a celebration) and this class picks the best primitive the device supports.
 *
 * <p>View-based feedback goes through {@link View#performHapticFeedback}, so it also honours the
 * system "touch feedback" setting. Everything is gated by the in-app Haptic feedback preference
 * ({@link #setEnabled}), which WinterArcApplication keeps in sync.
 */
public final class Haptics {
    private static volatile boolean enabled = true;

    private Haptics() {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** A light detent: selecting a card, chip, tab, page or grid cell. */
    public static void tick(@Nullable View view) {
        perform(view, Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.SEGMENT_TICK
                : HapticFeedbackConstants.CLOCK_TICK);
    }

    /** Ticks, then runs {@code action}; handy for one-line click listeners. */
    public static void tickThen(@Nullable View view, Runnable action) {
        tick(view);
        action.run();
    }

    /** Switching between top-level destinations. */
    public static void segment(@Nullable View view) {
        perform(view, Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                : HapticFeedbackConstants.CLOCK_TICK);
    }

    /** A switch or day toggle changing state. */
    public static void toggle(@Nullable View view, boolean on) {
        if (Build.VERSION.SDK_INT >= 34) {
            perform(view, on ? HapticFeedbackConstants.TOGGLE_ON : HapticFeedbackConstants.TOGGLE_OFF);
        } else {
            perform(view, HapticFeedbackConstants.CONTEXT_CLICK);
        }
    }

    /** A meaningful action succeeded: target completed, goal saved, code verified. */
    public static void confirm(@Nullable View view) {
        perform(view, Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** Input was rejected: wrong code, invalid field, blocked step. */
    public static void reject(@Nullable View view) {
        perform(view, Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.REJECT
                : HapticFeedbackConstants.LONG_PRESS);
    }

    /** A lighter acknowledgement for reversible actions such as undo or skip. */
    public static void soft(@Nullable View view) {
        perform(view, HapticFeedbackConstants.KEYBOARD_TAP);
    }

    /**
     * A short, rising "ta-da" for reaching a milestone. Uses composed primitives where the
     * hardware supports them, otherwise a gentle waveform.
     */
    public static void celebrate(@Nullable Context context) {
        if (!enabled || context == null) return;
        Vibrator vibrator = vibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 30 && vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD)) {
            vibrator.vibrate(VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 60)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 60)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 90)
                    .compose());
            return;
        }
        long[] timings = {0, 18, 60, 22, 60, 30, 90, 60};
        int[] amplitudes = {0, 70, 0, 110, 0, 160, 0, 220};
        vibrator.vibrate(vibrator.hasAmplitudeControl()
                ? VibrationEffect.createWaveform(timings, amplitudes, -1)
                : VibrationEffect.createWaveform(timings, -1));
    }

    private static void perform(@Nullable View view, int constant) {
        if (!enabled || view == null) return;
        view.performHapticFeedback(constant);
    }

    @Nullable
    private static Vibrator vibrator(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return context.getSystemService(Vibrator.class);
    }
}
