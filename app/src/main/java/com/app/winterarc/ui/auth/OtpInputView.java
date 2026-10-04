package com.app.winterarc.ui.auth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;

import com.app.winterarc.R;
import com.app.winterarc.ui.common.Ui;

/**
 * One-time-code field drawn as separate boxes. It stays a single EditText, so SMS code autofill
 * ("oneTimeCode"), paste, keyboard suggestions and TalkBack all work without juggling six views.
 */
public class OtpInputView extends AppCompatEditText {

    public interface OnCodeCompleteListener {
        void onCodeComplete(String code);
    }

    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int strokeColor;
    private final int activeColor;
    private final int errorColor;
    private final int fillColor;
    private int length = 6;
    private boolean error;
    @Nullable private OnCodeCompleteListener listener;

    public OtpInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        strokeColor = ContextCompat.getColor(context, R.color.wa_outline);
        activeColor = ContextCompat.getColor(context, R.color.wa_primary);
        errorColor = ContextCompat.getColor(context, R.color.wa_error);
        fillColor = ContextCompat.getColor(context, R.color.wa_surface);
        digit.setColor(ContextCompat.getColor(context, R.color.wa_on_surface));
        digit.setTextAlign(Paint.Align.CENTER);
        digit.setTextSize(getResources().getDisplayMetrics().scaledDensity * 26);
        digit.setFakeBoldText(true);
        box.setStrokeWidth(Ui.dp(context, 1.5f));

        setInputType(InputType.TYPE_CLASS_NUMBER);
        setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
        setBackground(null);
        setCursorVisible(false);
        setTextColor(Color.TRANSPARENT);
        setHighlightColor(Color.TRANSPARENT);
        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        // androidx.autofill HintConstants.AUTOFILL_HINT_SMS_OTP, plus the generic one-time-code hint.
        setAutofillHints("smsOTPCode", "oneTimeCode");
        setMinHeight(Ui.dp(context, 64));
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                // Keep only digits (pasted text like "123 456" or "Code: 123456").
                String digits = s.toString().replaceAll("\\D", "");
                if (!digits.equals(s.toString())) {
                    setText(digits.length() > length ? digits.substring(0, length) : digits);
                    return;
                }
                setSelection(s.length());
                if (error) setError(false);
                invalidate();
                if (s.length() == length && listener != null) listener.onCodeComplete(s.toString());
            }
        });
    }

    public void setCodeLength(int value) {
        length = value;
        setFilters(new InputFilter[]{new InputFilter.LengthFilter(value)});
        invalidate();
    }

    public void setOnCodeCompleteListener(@Nullable OnCodeCompleteListener l) {
        listener = l;
    }

    /** Red outlines after a rejected code; cleared as soon as the user edits. */
    public void setError(boolean value) {
        error = value;
        invalidate();
    }

    public String code() {
        return getText() == null ? "" : getText().toString();
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        // The caret always sits at the end; boxes show the position instead.
        CharSequence text = getText();
        if (text != null && (selStart != text.length() || selEnd != text.length())) {
            setSelection(text.length());
            return;
        }
        super.onSelectionChanged(selStart, selEnd);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        // EditText scrolls its canvas to keep the (invisible) text in view; draw in view space.
        canvas.save();
        canvas.translate(getScrollX(), getScrollY());
        drawBoxes(canvas);
        canvas.restore();
    }

    private void drawBoxes(Canvas canvas) {
        String text = code();
        float gap = Ui.dp(getContext(), 10);
        float available = getWidth() - getPaddingLeft() - getPaddingRight();
        float size = Math.min(Ui.dp(getContext(), 56), (available - gap * (length - 1)) / length);
        float total = size * length + gap * (length - 1);
        float left = getPaddingLeft() + (available - total) / 2f;
        float top = (getHeight() - size) / 2f;
        float radius = Ui.dp(getContext(), 14);
        Paint.FontMetrics fm = digit.getFontMetrics();
        for (int i = 0; i < length; i++) {
            float x = left + i * (size + gap);
            rect.set(x, top, x + size, top + size);
            box.setStyle(Paint.Style.FILL);
            box.setColor(fillColor);
            canvas.drawRoundRect(rect, radius, radius, box);
            boolean active = isFocused() && i == Math.min(text.length(), length - 1);
            box.setStyle(Paint.Style.STROKE);
            box.setColor(error ? errorColor : active ? activeColor : strokeColor);
            box.setStrokeWidth(Ui.dp(getContext(), active || error ? 2f : 1.5f));
            canvas.drawRoundRect(rect, radius, radius, box);
            if (i < text.length()) {
                canvas.drawText(text.substring(i, i + 1), rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, digit);
            }
        }
    }
}
