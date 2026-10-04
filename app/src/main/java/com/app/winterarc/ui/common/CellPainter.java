package com.app.winterarc.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.app.winterarc.R;
import com.app.winterarc.domain.engine.Heatmap;

/**
 * Draws one contribution cell. Shared by the grid and its legend so both always match. Each level
 * has a shape cue in addition to color: half-fill for partial, a dot for rest, an outline for
 * future days and an inner mark for exceeded days.
 */
public final class CellPainter {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    @ColorInt private final int emptyColor;
    @ColorInt private final int futureStroke;
    @ColorInt private final int restMarker;
    @ColorInt private final int todayRing;
    @ColorInt private final int surfaceColor;
    private final float radius;

    public CellPainter(Context context) {
        emptyColor = ContextCompat.getColor(context, R.color.wa_grid_empty);
        futureStroke = ContextCompat.getColor(context, R.color.wa_grid_future_stroke);
        restMarker = ContextCompat.getColor(context, R.color.wa_grid_rest_marker);
        todayRing = ContextCompat.getColor(context, R.color.wa_grid_today_ring);
        surfaceColor = ContextCompat.getColor(context, R.color.wa_surface);
        radius = context.getResources().getDimension(R.dimen.grid_cell_radius);
        stroke.setStyle(Paint.Style.STROKE);
    }

    public void draw(Canvas canvas, Heatmap.CellLevel level, boolean today, RectF r, @ColorInt int goalColor) {
        float cell = r.width();
        fill.setStyle(Paint.Style.FILL);
        switch (level) {
            case OUTSIDE:
                fill.setColor(ColorUtils.setAlphaComponent(emptyColor, 110));
                canvas.drawRoundRect(r, radius, radius, fill);
                break;
            case FUTURE:
                stroke.setColor(futureStroke);
                stroke.setStrokeWidth(Math.max(1f, cell / 12f));
                float inset = stroke.getStrokeWidth() / 2f;
                canvas.drawRoundRect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset, radius, radius, stroke);
                break;
            case EMPTY:
                fill.setColor(emptyColor);
                canvas.drawRoundRect(r, radius, radius, fill);
                break;
            case REST:
                fill.setColor(emptyColor);
                canvas.drawRoundRect(r, radius, radius, fill);
                fill.setColor(restMarker);
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.14f, fill);
                break;
            case PARTIAL:
                fill.setColor(emptyColor);
                canvas.drawRoundRect(r, radius, radius, fill);
                canvas.save();
                canvas.clipRect(r.left, r.centerY(), r.right, r.bottom);
                fill.setColor(ColorUtils.blendARGB(emptyColor, goalColor, 0.55f));
                canvas.drawRoundRect(r, radius, radius, fill);
                canvas.restore();
                break;
            case COMPLETED:
                fill.setColor(ColorUtils.blendARGB(emptyColor, goalColor, 0.72f));
                canvas.drawRoundRect(r, radius, radius, fill);
                break;
            case EXCEEDED:
                fill.setColor(goalColor);
                canvas.drawRoundRect(r, radius, radius, fill);
                fill.setColor(ColorUtils.setAlphaComponent(surfaceColor, 200));
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.12f, fill);
                break;
        }
        if (today) {
            stroke.setColor(todayRing);
            stroke.setStrokeWidth(Math.max(1.5f, cell / 9f));
            float o = stroke.getStrokeWidth();
            canvas.drawRoundRect(r.left - o, r.top - o, r.right + o, r.bottom + o, radius + o, radius + o, stroke);
        }
    }
}
