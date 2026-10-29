package com.app.winterarc.ui.feed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.app.winterarc.ui.common.Ui;
import com.google.android.material.color.MaterialColors;

/**
 * Draws the connectors of a comment thread behind one comment row: pass-through lines for
 * ancestors that have more replies below, a curved elbow from the parent's line into this
 * comment's avatar, and a line down from this avatar when the comment has replies.
 *
 * <p>Geometry matches item_comment.xml: content starts at {@link #GUTTER_DP}, each level indents by
 * {@link #INDENT_DP}, and the avatar is {@link #AVATAR_DP} square, {@link #TOP_DP} from the top.
 */
public class ThreadLinesView extends View {
    static final int GUTTER_DP = 16;
    static final int INDENT_DP = 32;
    static final int AVATAR_DP = 32;
    static final int TOP_DP = 12;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float gutter;
    private final float indent;
    private final float avatar;
    private final float top;
    private final float gap;
    private final float elbow;
    private int depth;
    private boolean[] continues = new boolean[0];
    private boolean hasChildren;

    public ThreadLinesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gutter = Ui.dp(context, GUTTER_DP);
        indent = Ui.dp(context, INDENT_DP);
        avatar = Ui.dp(context, AVATAR_DP);
        top = Ui.dp(context, TOP_DP);
        gap = Ui.dp(context, 4);
        elbow = Ui.dp(context, 10);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(context, 2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline));
        setWillNotDraw(false);
    }

    void bind(int depth, boolean[] continues, boolean hasChildren) {
        this.depth = depth;
        this.continues = continues;
        this.hasChildren = hasChildren;
        invalidate();
    }

    private float column(int level) {
        return gutter + level * indent + avatar / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float avatarCenter = top + avatar / 2f;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (rtl) {
            canvas.save();
            canvas.scale(-1f, 1f, getWidth() / 2f, 0f);
        }
        for (int c = 0; c < depth; c++) {
            float x = column(c);
            boolean carriesOn = c < continues.length && continues[c];
            if (c < depth - 1) {
                if (carriesOn) canvas.drawLine(x, 0, x, h, paint);
                continue;
            }
            // Parent's column: elbow into this avatar, and keep going if later siblings follow.
            float childEdge = gutter + depth * indent - gap;
            path.reset();
            path.moveTo(x, 0);
            path.lineTo(x, avatarCenter - elbow);
            path.quadTo(x, avatarCenter, x + elbow, avatarCenter);
            path.lineTo(childEdge, avatarCenter);
            canvas.drawPath(path, paint);
            if (carriesOn) canvas.drawLine(x, avatarCenter - elbow, x, h, paint);
        }
        if (hasChildren) {
            float x = column(depth);
            canvas.drawLine(x, top + avatar + gap, x, h, paint);
        }
        if (rtl) canvas.restore();
    }
}
