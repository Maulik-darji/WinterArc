package com.app.winterarc.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.app.winterarc.R;
import com.app.winterarc.domain.model.Amount;

import java.util.List;

/**
 * A row of rounded bars visualising a sequence of targets: flat for consistency, rising for a
 * progression plan. Bars up to {@code completedCount} are filled; the rest are tinted.
 * Decorative: callers provide the text equivalent.
 */
public class StepBarsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float[] heights = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
    private int completedCount = 4;
    @ColorInt private int color;
    @ColorInt private final int track;

    public StepBarsView(Context context) {
        this(context, null);
    }

    public StepBarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        color = ContextCompat.getColor(context, R.color.wa_primary);
        track = ContextCompat.getColor(context, R.color.wa_grid_empty);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setFlat(int count, int completed, @ColorInt int barColor) {
        heights = new float[count];
        for (int i = 0; i < count; i++) heights[i] = 0.55f;
        completedCount = completed;
        color = barColor;
        invalidate();
    }

    public void setRising(int count, int completed, @ColorInt int barColor) {
        heights = new float[count];
        for (int i = 0; i < count; i++) heights[i] = 0.2f + 0.8f * (i + 1) / count;
        completedCount = completed;
        color = barColor;
        invalidate();
    }

    /** Bars proportional to real plan steps, e.g. 1 → 2 → … → 10 km. Shows at most 24 bars. */
    public void setSteps(List<Amount> steps, int completed, @ColorInt int barColor) {
        int n = Math.min(steps.size(), 24);
        heights = new float[n];
        double max = steps.get(steps.size() - 1).toDouble();
        for (int i = 0; i < n; i++) {
            int index = steps.size() <= 24 ? i : Math.round(i * (steps.size() - 1) / (float) (n - 1));
            heights[i] = (float) Math.max(0.12, steps.get(index).toDouble() / max);
        }
        completedCount = steps.size() <= 24 ? completed : Math.round(completed * n / (float) steps.size());
        color = barColor;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int n = heights.length;
        if (n == 0) return;
        float gap = Ui.dp(getContext(), 4);
        float w = (getWidth() - getPaddingLeft() - getPaddingRight() - gap * (n - 1)) / n;
        float h = getHeight() - getPaddingTop() - getPaddingBottom();
        float radius = Math.min(w / 2f, Ui.dp(getContext(), 4));
        for (int i = 0; i < n; i++) {
            float left = getPaddingLeft() + i * (w + gap);
            float top = getPaddingTop() + h * (1f - heights[i]);
            rect.set(left, top, left + w, getPaddingTop() + h);
            paint.setColor(i < completedCount ? color : ColorUtils.blendARGB(track, color, 0.18f));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
