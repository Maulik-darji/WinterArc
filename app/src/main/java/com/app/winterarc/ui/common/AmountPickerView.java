package com.app.winterarc.ui.common;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.GoalUnit;

import java.text.DecimalFormatSymbols;

/**
 * Wheel picker built from one NumberPicker per digit: two columns for 00 to 99, an optional third
 * column for units that commonly exceed 99 (minutes, pages, reps), and a tenths column for units
 * that support decimals (e.g. 0.5 km). Values are read and written as exact {@link Amount}s.
 */
public class AmountPickerView extends LinearLayout {

    public interface OnAmountChangedListener {
        void onAmountChanged(Amount amount);
    }

    private final NumberPicker hundreds;
    private final NumberPicker tens;
    private final NumberPicker ones;
    private final TextView separator;
    private final NumberPicker tenths;
    private final TextView unitLabel;
    @Nullable private OnAmountChangedListener listener;
    private boolean notifying = true;

    public AmountPickerView(Context context) {
        this(context, null);
    }

    public AmountPickerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        hundreds = digit(R.string.picker_cd_hundreds);
        tens = digit(R.string.picker_cd_tens);
        ones = digit(R.string.picker_cd_ones);
        separator = new TextView(context);
        separator.setText(String.valueOf(DecimalFormatSymbols.getInstance(Formats.locale(context)).getDecimalSeparator()));
        separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        separator.setTextColor(ContextCompat.getColor(context, R.color.wa_on_surface));
        separator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        tenths = digit(R.string.picker_cd_tenths);
        unitLabel = new TextView(context);
        unitLabel.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        unitLabel.setPaddingRelative(Ui.dp(context, 12), 0, 0, 0);
        unitLabel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(hundreds);
        addView(tens);
        addView(ones);
        addView(separator);
        addView(tenths);
        addView(unitLabel);
        configure(GoalUnit.KILOMETERS, null);
    }

    private NumberPicker digit(int descriptionRes) {
        NumberPicker picker = new NumberPicker(getContext());
        picker.setMinValue(0);
        picker.setMaxValue(9);
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        picker.setContentDescription(getContext().getString(descriptionRes));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28, getResources().getDisplayMetrics()));
            picker.setTextColor(ContextCompat.getColor(getContext(), R.color.wa_on_surface));
        }
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(Ui.dp(getContext(), 2));
        lp.setMarginEnd(Ui.dp(getContext(), 2));
        picker.setLayoutParams(lp);
        picker.setOnValueChangedListener((p, oldVal, newVal) -> onChanged());
        return picker;
    }

    /** Shows the columns appropriate for {@code unit}. Keeps the current value where possible. */
    public void configure(GoalUnit unit, @Nullable String customLabel) {
        Amount current = getAmount();
        boolean decimals = unit.supportsDecimal();
        boolean bigNumbers = !unit.isDistance() && unit != GoalUnit.CUSTOM;
        hundreds.setVisibility(bigNumbers ? VISIBLE : GONE);
        separator.setVisibility(decimals ? VISIBLE : GONE);
        tenths.setVisibility(decimals ? VISIBLE : GONE);
        unitLabel.setText(Formats.unitLabel(getContext(), unit, customLabel));
        setAmountSilently(current);
    }

    public Amount maxValue() {
        long whole = hundreds.getVisibility() == VISIBLE ? 999 : 99;
        int t = tenths.getVisibility() == VISIBLE ? 9 : 0;
        return Amount.of((int) whole, t);
    }

    public void setAmount(Amount amount) {
        setAmountSilently(amount);
        announce();
    }

    private void setAmountSilently(Amount amount) {
        notifying = false;
        Amount clamped = Amount.min(amount, maxValue());
        long whole = clamped.wholePart();
        hundreds.setValue((int) (whole / 100 % 10));
        tens.setValue((int) (whole / 10 % 10));
        ones.setValue((int) (whole % 10));
        tenths.setValue(tenths.getVisibility() == VISIBLE ? clamped.tenthsPart() : 0);
        notifying = true;
        updateDescription();
    }

    public Amount getAmount() {
        if (hundreds == null) return Amount.ZERO;
        int whole = (hundreds.getVisibility() == VISIBLE ? hundreds.getValue() * 100 : 0)
                + tens.getValue() * 10 + ones.getValue();
        int t = tenths.getVisibility() == VISIBLE ? tenths.getValue() : 0;
        return Amount.of(whole, t);
    }

    public void setOnAmountChangedListener(@Nullable OnAmountChangedListener l) {
        listener = l;
    }

    private void onChanged() {
        updateDescription();
        if (notifying) announce();
    }

    private void announce() {
        if (listener != null) listener.onAmountChanged(getAmount());
    }

    private void updateDescription() {
        String value = Formats.amount(getContext(), getAmount()) + " " + unitLabel.getText();
        setContentDescription(getContext().getString(R.string.picker_value_cd, value));
    }
}
