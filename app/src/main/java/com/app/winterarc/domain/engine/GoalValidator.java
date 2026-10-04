package com.app.winterarc.domain.engine;

import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.GoalDraft;
import com.app.winterarc.domain.model.GoalUnit;
import com.app.winterarc.domain.model.TrackingMode;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class GoalValidator {

    public static final int MAX_TITLE_LENGTH = 60;
    public static final int MAX_DESCRIPTION_LENGTH = 280;
    public static final int MAX_CUSTOM_UNIT_LENGTH = 16;
    public static final Amount MAX_TARGET = Amount.whole(9_999);

    private GoalValidator() {}

    public enum Error {
        TITLE_BLANK,
        TITLE_TOO_LONG,
        DESCRIPTION_TOO_LONG,
        CUSTOM_UNIT_BLANK,
        TARGET_NOT_POSITIVE,
        TARGET_TOO_LARGE,
        START_NOT_POSITIVE,
        FINAL_NOT_GREATER_THAN_START,
        FINAL_TOO_LARGE,
        HOP_NOT_POSITIVE,
        TOO_MANY_STEPS,
        DECIMALS_NOT_SUPPORTED,
        NO_ACTIVE_DAYS
    }

    public static Set<Error> validate(GoalDraft draft) {
        Set<Error> errors = EnumSet.noneOf(Error.class);
        String title = draft.title == null ? "" : draft.title.trim();
        if (title.isEmpty()) errors.add(Error.TITLE_BLANK);
        if (title.length() > MAX_TITLE_LENGTH) errors.add(Error.TITLE_TOO_LONG);
        if (draft.description != null && draft.description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            errors.add(Error.DESCRIPTION_TOO_LONG);
        }
        if (draft.unit == GoalUnit.CUSTOM && (draft.customUnitLabel == null || draft.customUnitLabel.isBlank())) {
            errors.add(Error.CUSTOM_UNIT_BLANK);
        }
        if (draft.schedule.isEmpty()) errors.add(Error.NO_ACTIVE_DAYS);
        errors.addAll(validateTargets(draft));
        return errors;
    }

    public static Set<Error> validateTargets(GoalDraft draft) {
        Set<Error> errors = EnumSet.noneOf(Error.class);
        List<Amount> amounts;
        if (draft.trackingMode == TrackingMode.CONSISTENCY) {
            amounts = List.of(draft.consistencyTarget);
            if (!draft.consistencyTarget.isPositive()) errors.add(Error.TARGET_NOT_POSITIVE);
            if (draft.consistencyTarget.greaterThan(MAX_TARGET)) errors.add(Error.TARGET_TOO_LARGE);
        } else {
            amounts = List.of(draft.startTarget, draft.finalTarget, draft.hop);
            if (!draft.startTarget.isPositive()) errors.add(Error.START_NOT_POSITIVE);
            if (!draft.finalTarget.greaterThan(draft.startTarget)) errors.add(Error.FINAL_NOT_GREATER_THAN_START);
            if (draft.finalTarget.greaterThan(MAX_TARGET)) errors.add(Error.FINAL_TOO_LARGE);
            if (!draft.hop.isPositive()) {
                errors.add(Error.HOP_NOT_POSITIVE);
            } else if (draft.finalTarget.greaterThan(draft.startTarget)) {
                long steps = draft.finalTarget.minus(draft.startTarget).milli() / draft.hop.milli() + 1;
                if (steps >= ProgressionEngine.MAX_STEPS) errors.add(Error.TOO_MANY_STEPS);
            }
        }
        if (!draft.unit.supportsDecimal()) {
            for (Amount a : amounts) {
                if (!a.isWhole()) {
                    errors.add(Error.DECIMALS_NOT_SUPPORTED);
                    break;
                }
            }
        }
        return errors;
    }
}
