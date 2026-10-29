package com.app.winterarc.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.domain.engine.Heatmap;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Contribution grid: one rounded square per day, one column per week. Drawn on a single Canvas so a
 * full year stays smooth, with an ExploreByTouchHelper exposing each day to TalkBack.
 *
 * <p>Status is never conveyed by color alone: partial days are half-filled, rest days carry a dot,
 * future days are outlined, exceeded days carry an inner mark, and every day has a spoken label.
 */
public class ContributionGridView extends View {

    public interface OnDayClickListener {
        void onDayClick(Heatmap.Day day);
    }

    private final CellPainter painter;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final GridAccessibility accessibility;

    @ColorInt private int goalColor;

    private float labelTextSize;
    private boolean compact;
    private boolean large;
    private final Paint dayNumber = new Paint(Paint.ANTI_ALIAS_FLAG);
    @ColorInt private final int dayNumberColor;
    private float cell;
    private float gap;
    private float labelWidth;
    private float labelHeight;

    @Nullable private Heatmap heatmap;
    @Nullable private OnDayClickListener listener;
    private Locale locale = Locale.getDefault();

    public ContributionGridView(Context context) {
        this(context, null);
    }

    public ContributionGridView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        painter = new CellPainter(context);
        goalColor = ContextCompat.getColor(context, R.color.goal_emerald);
        labelTextSize = getResources().getDimension(R.dimen.grid_label_text);
        text.setTextSize(labelTextSize);
        text.setColor(ContextCompat.getColor(context, R.color.wa_on_surface_variant));
        dayNumberColor = ContextCompat.getColor(context, R.color.wa_on_surface_variant);
        dayNumber.setTextAlign(Paint.Align.LEFT);
        accessibility = new GridAccessibility(this);
        ViewCompat.setAccessibilityDelegate(this, accessibility);
        setCompact(false);
        if (isInEditMode()) {
            setData(PreviewData.heatmap(java.time.LocalDate.now(), 20, DayOfWeek.MONDAY, 3), goalColor, Locale.getDefault());
        }
    }

    /** Compact grids (home cards) have no labels and are announced as a single summary. */
    public void setCompact(boolean value) {
        compact = value;
        Context c = getContext();
        cell = getResources().getDimension(value ? R.dimen.grid_cell_compact : R.dimen.grid_cell);
        gap = getResources().getDimension(value ? R.dimen.grid_gap_compact : R.dimen.grid_gap);
        labelWidth = value ? 0 : Ui.dp(c, 26);
        labelHeight = value ? 0 : labelTextSize + Ui.dp(c, 8);
        setImportantForAccessibility(value ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        setFocusable(!value);
        requestLayout();
    }

    /**
     * Large grids (the zoom sheet): about 2.5x bigger squares with the day of the month in each,
     * so a finger lands on the intended day.
     */
    public void setLarge(boolean value) {
        large = value;
        compact = false;
        Context c = getContext();
        cell = getResources().getDimension(value ? R.dimen.grid_cell_large : R.dimen.grid_cell);
        gap = getResources().getDimension(value ? R.dimen.grid_gap_large : R.dimen.grid_gap);
        labelTextSize = getResources().getDimension(value ? R.dimen.grid_label_text_large : R.dimen.grid_label_text);
        text.setTextSize(labelTextSize);
        dayNumber.setTextSize(cell * 0.34f);
        labelWidth = Ui.dp(c, value ? 34 : 26);
        labelHeight = labelTextSize + Ui.dp(c, 8);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        setFocusable(true);
        requestLayout();
        invalidate();
    }

    /** Horizontal centre of the week column containing {@code date}, or -1 when not shown. */
    public float centerXOf(java.time.LocalDate date) {
        Heatmap h = heatmap;
        if (h == null) return -1;
        for (int w = 0; w < h.weeks.size(); w++) {
            for (Heatmap.Day d : h.weeks.get(w).days()) {
                if (d.date().equals(date)) return getPaddingLeft() + labelWidth + w * (cell + gap) + cell / 2f;
            }
        }
        return -1;
    }

    public void setData(Heatmap heatmap, @ColorInt int goalColor, Locale locale) {
        this.heatmap = heatmap;
        this.goalColor = goalColor;
        this.locale = locale;
        accessibility.invalidateRoot();
        requestLayout();
        invalidate();
    }

    public void setOnDayClickListener(@Nullable OnDayClickListener l) {
        listener = l;
        setClickable(l != null || tapAnywhere != null);
    }

    /**
     * For small grids: any tap (not only on a selectable day) reports the nearest day, so the
     * caller can open the enlarged grid there. Exact day picking happens in the large grid.
     */
    public void setOnTapAnywhereListener(@Nullable OnDayClickListener l) {
        tapAnywhere = l;
        setClickable(l != null || listener != null);
    }

    @Nullable private OnDayClickListener tapAnywhere;

    /** The day under (x, y), clamped to the grid so taps on labels or margins still map to a day. */
    @Nullable
    Heatmap.Day nearestDay(float x, float y) {
        Heatmap h = heatmap;
        if (h == null || h.weeks.isEmpty()) return null;
        int w = (int) Math.floor((x - getPaddingLeft() - offsetX() - labelWidth) / (cell + gap));
        int d = (int) Math.floor((y - getPaddingTop() - labelHeight) / (cell + gap));
        w = Math.max(0, Math.min(h.weeks.size() - 1, w));
        d = Math.max(0, Math.min(6, d));
        return h.weeks.get(w).days().get(d);
    }

    /** Number of week columns that fit in {@code widthPx} in full (non-compact) mode. */
    public static int weeksThatFit(Context context, int widthPx) {
        float cell = context.getResources().getDimension(R.dimen.grid_cell);
        float gap = context.getResources().getDimension(R.dimen.grid_gap);
        float label = Ui.dp(context, 26);
        return Math.max(4, (int) ((widthPx - label + gap) / (cell + gap)));
    }

    private int weeks() {
        return heatmap == null ? 0 : heatmap.weeks.size();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int weeks = Math.max(1, weeks());
        if (compact && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            // Compact grids stretch to the available width, capped at a comfortable size.
            int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
            float fitted = (available - (weeks - 1) * gap) / weeks;
            cell = Math.min(fitted, Ui.dp(getContext(), 12));
        }
        int w = (int) Math.ceil(getPaddingLeft() + getPaddingRight() + labelWidth + weeks * cell + (weeks - 1) * gap);
        int h = (int) Math.ceil(getPaddingTop() + getPaddingBottom() + labelHeight + 7 * cell + 6 * gap);
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Heatmap h = heatmap;
        if (h == null) return;
        if (!compact) drawLabels(canvas, h);
        for (int w = 0; w < h.weeks.size(); w++) {
            List<Heatmap.Day> days = h.weeks.get(w).days();
            for (int d = 0; d < 7; d++) {
                cellRect(w, d, rect);
                drawCell(canvas, days.get(d), rect);
            }
        }
    }

    private void drawLabels(Canvas canvas, Heatmap h) {
        float baseline = getPaddingTop() + labelTextSize;
        float lastLabelEnd = -1;
        for (int w = 0; w < h.weeks.size(); w++) {
            Heatmap.Week week = h.weeks.get(w);
            if (week.monthStart() == null) continue;
            String label = week.monthStart().getMonth().getDisplayName(TextStyle.SHORT, locale);
            float x = getPaddingLeft() + labelWidth + w * (cell + gap);
            // Keep the last label inside the view, and never overlap the previous one.
            x = Math.min(x, getWidth() - getPaddingRight() - text.measureText(label));
            if (x < lastLabelEnd) continue;
            canvas.drawText(label, x, baseline, text);
            lastLabelEnd = x + text.measureText(label) + gap * 2;
        }
        // Weekday labels on rows 1, 3, 5 (e.g. Tue/Thu/Sat or Mon/Wed/Fri depending on locale).
        for (int d = 1; d < 7; d += 2) {
            DayOfWeek dow = h.firstDayOfWeek.plus(d);
            String label = dow.getDisplayName(TextStyle.SHORT, locale);
            if (label.length() > 3) label = label.substring(0, 3);
            float y = getPaddingTop() + labelHeight + d * (cell + gap) + cell * 0.8f;
            canvas.drawText(label, getPaddingLeft(), y, text);
        }
    }

    private void drawCell(Canvas canvas, Heatmap.Day day, RectF r) {
        painter.draw(canvas, day.level(), day.isToday(), r, goalColor);
        if (!large || day.level() == Heatmap.CellLevel.OUTSIDE) return;
        boolean filled = day.level() == Heatmap.CellLevel.COMPLETED || day.level() == Heatmap.CellLevel.EXCEEDED;
        dayNumber.setColor(filled ? 0xFFFFFFFF : dayNumberColor);
        dayNumber.setAlpha(day.level() == Heatmap.CellLevel.FUTURE ? 110 : 235);
        float inset = cell * 0.13f;
        canvas.drawText(String.valueOf(day.date().getDayOfMonth()), r.left + inset, r.top + inset + dayNumber.getTextSize() * 0.8f, dayNumber);
    }

    /** Horizontal offset that centers compact grids narrower than their view. */
    private float offsetX() {
        if (!compact || heatmap == null) return 0f;
        int weeks = heatmap.weeks.size();
        float content = weeks * cell + (weeks - 1) * gap;
        return Math.max(0f, (getWidth() - getPaddingLeft() - getPaddingRight() - content) / 2f);
    }

    void cellRect(int week, int dayIndex, RectF out) {
        float left = getPaddingLeft() + offsetX() + labelWidth + week * (cell + gap);
        float top = getPaddingTop() + labelHeight + dayIndex * (cell + gap);
        out.set(left, top, left + cell, top + cell);
    }

    @Nullable
    Heatmap.Day dayAt(float x, float y) {
        Heatmap h = heatmap;
        if (h == null) return null;
        int w = (int) ((x - getPaddingLeft() - labelWidth) / (cell + gap));
        int d = (int) ((y - getPaddingTop() - labelHeight) / (cell + gap));
        if (x < getPaddingLeft() + labelWidth || y < getPaddingTop() + labelHeight) return null;
        if (w < 0 || w >= h.weeks.size() || d < 0 || d > 6) return null;
        return h.weeks.get(w).days().get(d);
    }

    static boolean isInteractive(Heatmap.Day day) {
        return day.level() != Heatmap.CellLevel.FUTURE && day.level() != Heatmap.CellLevel.OUTSIDE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (tapAnywhere != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Heatmap.Day day = nearestDay(event.getX(), event.getY());
                performClick();
                if (day != null) tapAnywhere.onDayClick(day);
                return true;
            }
            return super.onTouchEvent(event);
        }
        if (listener == null || compact) return super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            Heatmap.Day day = dayAt(event.getX(), event.getY());
            if (day != null && isInteractive(day)) {
                pendingClick = day;
                performClick();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Nullable private Heatmap.Day pendingClick;

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        Heatmap.Day day = pendingClick;
        pendingClick = null;
        if (day != null && listener != null) {
            listener.onDayClick(day);
            return true;
        }
        return handled;
    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        return (!compact && accessibility.dispatchHoverEvent(event)) || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return (!compact && accessibility.dispatchKeyEvent(event)) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        accessibility.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    /** Spoken label for a day, e.g. "Thursday, 1 October: target completed". */
    String describe(Heatmap.Day day) {
        String date = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(day.date());
        return getContext().getString(R.string.grid_cd_day, date,
                getContext().getString(GoalVisuals.dayKind(day.kind())));
    }

    /** Exposes every past and present day as a virtual TalkBack node. */
    private static final class GridAccessibility extends ExploreByTouchHelper {
        private final ContributionGridView grid;
        private final RectF tmp = new RectF();

        GridAccessibility(ContributionGridView grid) {
            super(grid);
            this.grid = grid;
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            Heatmap.Day day = grid.dayAt(x, y);
            if (day == null || !isInteractive(day) || grid.compact) return INVALID_ID;
            return idOf(day);
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> ids) {
            Heatmap h = grid.heatmap;
            if (h == null || grid.compact) return;
            for (int w = 0; w < h.weeks.size(); w++) {
                for (int d = 0; d < 7; d++) {
                    if (isInteractive(h.weeks.get(w).days().get(d))) ids.add(w * 7 + d);
                }
            }
        }

        private int idOf(Heatmap.Day day) {
            Heatmap h = grid.heatmap;
            for (int w = 0; w < h.weeks.size(); w++) {
                int d = h.weeks.get(w).days().indexOf(day);
                if (d >= 0) return w * 7 + d;
            }
            return INVALID_ID;
        }

        @Override
        protected void onPopulateNodeForVirtualView(int id, @NonNull AccessibilityNodeInfoCompat node) {
            Heatmap h = grid.heatmap;
            if (h == null || id / 7 >= h.weeks.size()) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect());
                return;
            }
            Heatmap.Day day = h.weeks.get(id / 7).days().get(id % 7);
            node.setContentDescription(grid.describe(day));
            grid.cellRect(id / 7, id % 7, tmp);
            // Expand bounds so each virtual target is comfortably focusable.
            Rect bounds = new Rect();
            tmp.roundOut(bounds);
            node.setBoundsInParent(bounds);
            if (grid.listener != null) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                node.setClickable(true);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int id, int action, @Nullable Bundle args) {
            Heatmap h = grid.heatmap;
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || grid.listener == null || h == null) return false;
            grid.listener.onDayClick(h.weeks.get(id / 7).days().get(id % 7));
            return true;
        }
    }

    /** Summary used as the content description of compact grids. */
    public static String summary(Context context, String title, Heatmap heatmap) {
        int completed = 0;
        for (Heatmap.Week w : heatmap.weeks) {
            for (Heatmap.Day d : w.days()) if (d.kind().isCompletion()) completed++;
        }
        return context.getResources().getQuantityString(R.plurals.grid_cd_summary, completed, title, completed,
                heatmap.weeks.size());
    }

    /** Locale of the current configuration, for month and weekday labels. */
    public static Locale localeOf(Context context) {
        return Formats.locale(context);
    }
}
