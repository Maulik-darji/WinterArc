package com.app.winterarc.ui.detail;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.app.winterarc.R;
import com.app.winterarc.ui.common.Ui;

import java.util.Random;

/**
 * A short, tasteful burst of falling squares in the goal palette — echoing the contribution grid.
 * Plays once; does nothing when the user has disabled animations.
 */
public class ConfettiView extends View {
    private static final int COUNT = 70;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] x = new float[COUNT];
    private final float[] speed = new float[COUNT];
    private final float[] drift = new float[COUNT];
    private final float[] spin = new float[COUNT];
    private final float[] size = new float[COUNT];
    private final int[] color = new int[COUNT];
    private float progress = -1f;
    @Nullable private ValueAnimator animator;

    public ConfettiView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void play() {
        if (Ui.reducedMotion(getContext())) return;
        Random random = new Random();
        int[] palette = {
                ContextCompat.getColor(getContext(), R.color.goal_emerald),
                ContextCompat.getColor(getContext(), R.color.goal_teal),
                ContextCompat.getColor(getContext(), R.color.goal_violet),
                ContextCompat.getColor(getContext(), R.color.goal_amber),
                ContextCompat.getColor(getContext(), R.color.goal_sky)};
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < COUNT; i++) {
            x[i] = random.nextFloat();
            speed[i] = 0.6f + random.nextFloat() * 0.8f;
            drift[i] = (random.nextFloat() - 0.5f) * 0.25f;
            spin[i] = (random.nextFloat() - 0.5f) * 720f;
            size[i] = (5 + random.nextInt(6)) * density;
            color[i] = palette[i % palette.length];
        }
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(getResources().getInteger(R.integer.motion_celebration));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (progress < 0f || progress >= 1f) return;
        float w = getWidth();
        float h = getHeight();
        int alpha = (int) (255 * Math.min(1f, (1f - progress) * 3f));
        for (int i = 0; i < COUNT; i++) {
            float t = progress * speed[i];
            float cx = (x[i] + drift[i] * t) * w;
            float cy = -size[i] + t * h * 1.1f;
            paint.setColor(color[i]);
            paint.setAlpha(alpha);
            canvas.save();
            canvas.rotate(spin[i] * t, cx, cy);
            rect.set(cx - size[i] / 2, cy - size[i] / 2, cx + size[i] / 2, cy + size[i] / 2);
            canvas.drawRoundRect(rect, size[i] / 4, size[i] / 4, paint);
            canvas.restore();
        }
    }
}
