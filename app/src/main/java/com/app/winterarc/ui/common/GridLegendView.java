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

import com.app.winterarc.R;
import com.app.winterarc.domain.engine.Heatmap;

/** "Less ▢▤▣■ More   ▢· Rest" legend drawn with the same painter as the grid. */
public class GridLegendView extends View {
    private static final Heatmap.CellLevel[] SCALE = {
            Heatmap.CellLevel.EMPTY, Heatmap.CellLevel.PARTIAL, Heatmap.CellLevel.COMPLETED, Heatmap.CellLevel.EXCEEDED};

    private final CellPainter painter;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float cell;
    private final float gap;
    @ColorInt private int goalColor;

    public GridLegendView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        painter = new CellPainter(context);
        cell = getResources().getDimension(R.dimen.grid_cell);
        gap = getResources().getDimension(R.dimen.grid_gap);
        text.setTextSize(getResources().getDimension(R.dimen.grid_label_text));
        text.setColor(ContextCompat.getColor(context, R.color.wa_on_surface_variant));
        goalColor = ContextCompat.getColor(context, R.color.goal_emerald);
        // Spoken equivalent of the legend.
        setContentDescription(context.getString(R.string.grid_legend_less) + ", "
                + context.getString(R.string.day_kind_partial) + ", " + context.getString(R.string.day_kind_completed)
                + ", " + context.getString(R.string.day_kind_exceeded) + ", " + context.getString(R.string.grid_legend_more)
                + ". " + context.getString(R.string.grid_legend_rest));
    }

    public void setGoalColor(@ColorInt int color) {
        goalColor = color;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = (int) Math.ceil(Math.max(cell, text.getTextSize() * 1.4f) + getPaddingTop() + getPaddingBottom());
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Context c = getContext();
        float y = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - cell) / 2f;
        float baseline = y + cell * 0.82f;
        String less = c.getString(R.string.grid_legend_less);
        String more = c.getString(R.string.grid_legend_more);
        String rest = c.getString(R.string.grid_legend_rest);
        float x = getPaddingLeft();
        canvas.drawText(less, x, baseline, text);
        x += text.measureText(less) + gap * 2;
        for (Heatmap.CellLevel level : SCALE) {
            rect.set(x, y, x + cell, y + cell);
            painter.draw(canvas, level, false, rect, goalColor);
            x += cell + gap;
        }
        x += gap;
        canvas.drawText(more, x, baseline, text);
        x += text.measureText(more) + gap * 6;
        rect.set(x, y, x + cell, y + cell);
        painter.draw(canvas, Heatmap.CellLevel.REST, false, rect, goalColor);
        x += cell + gap * 2;
        canvas.drawText(rest, x, baseline, text);
    }
}
