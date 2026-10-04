package com.app.winterarc.ui.editor;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.app.winterarc.R;
import com.app.winterarc.domain.model.Amount;

import java.util.ArrayList;
import java.util.List;

/**
 * Common walking/running milestones (km) with supportive messages. Every option is framed as
 * meaningful; none is presented as lesser than another.
 */
public final class MilestoneOptions {
    private MilestoneOptions() {}

    /** A suggestion; {@code value == null} represents the "Custom" option. */
    public record Option(@Nullable Amount value, @StringRes int message) {
        public boolean isCustom() {
            return value == null;
        }
    }

    private static final Option[] ALL = {
            new Option(Amount.whole(5), R.string.milestone_msg_5),
            new Option(Amount.whole(10), R.string.milestone_msg_10),
            new Option(Amount.whole(15), R.string.milestone_msg_15),
            new Option(Amount.whole(20), R.string.milestone_msg_20),
            new Option(new Amount(21_100), R.string.milestone_msg_21),
            new Option(Amount.whole(25), R.string.milestone_msg_25),
            new Option(Amount.whole(30), R.string.milestone_msg_30),
            new Option(Amount.whole(35), R.string.milestone_msg_35),
            new Option(Amount.whole(40), R.string.milestone_msg_40),
            new Option(new Amount(42_200), R.string.milestone_msg_42),
            new Option(Amount.whole(45), R.string.milestone_msg_45),
            new Option(Amount.whole(50), R.string.milestone_msg_50),
    };

    /** Suggestions above the starting target, followed by "Custom". */
    public static List<Option> above(Amount start) {
        List<Option> out = new ArrayList<>();
        for (Option o : ALL) if (o.value().greaterThan(start)) out.add(o);
        out.add(new Option(null, R.string.milestone_msg_custom));
        return out;
    }

    /** Index of the option matching {@code value}, or the custom option's index. */
    public static int indexOf(List<Option> options, Amount value) {
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (!o.isCustom() && o.value().equals(value)) return i;
        }
        return options.size() - 1;
    }
}
