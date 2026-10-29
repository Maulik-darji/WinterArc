package com.app.winterarc.ui.detail;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.ItemKeyValueBinding;
import com.app.winterarc.databinding.SheetDayDetailBinding;
import com.app.winterarc.domain.engine.DayKind;
import com.app.winterarc.domain.engine.GoalTimeline;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.ui.common.GoalVisuals;
import com.app.winterarc.ui.common.Haptics;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Details for one tapped grid square, on the detail and stats screens. A missed (or partial / rest)
 * past day can be marked as done, and a completed day cleared, so a forgotten check-in can be
 * fixed later. The use case refuses future days and days before the goal started.
 */
public class DayDetailSheet extends BottomSheetDialogFragment {
    private static final String ARG_DATE = "date";

    /** Implemented by parent fragments that can provide the goal and its timeline. */
    public interface Host {
        @Nullable Goal hostGoal();

        @Nullable GoalTimeline hostTimeline();

        LocalDate hostToday();

        /** Mark {@code date} as done (target met). */
        void hostMarkDone(LocalDate date);

        /** Remove the check-in for {@code date}. */
        void hostClearDay(LocalDate date);
    }

    public static DayDetailSheet newInstance(LocalDate date) {
        DayDetailSheet sheet = new DayDetailSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_DATE, date.toEpochDay());
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return SheetDayDetailBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        SheetDayDetailBinding b = SheetDayDetailBinding.bind(view);
        Fragment parent = requireParentFragment();
        if (!(parent instanceof Host)) {
            dismissAllowingStateLoss();
            return;
        }
        Host host = (Host) parent;
        Goal goal = host.hostGoal();
        GoalTimeline timeline = host.hostTimeline();
        if (goal == null || timeline == null) {
            dismissAllowingStateLoss();
            return;
        }
        Context c = requireContext();
        LocalDate date = LocalDate.ofEpochDay(requireArguments().getLong(ARG_DATE));
        DayKind kind = timeline.classify(date, host.hostToday());
        b.date.setText(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Formats.locale(c)).format(date));
        b.kind.setText(GoalVisuals.dayKind(kind));

        ProgressEntry entry = timeline.entriesByDate.get(date);
        bindActions(b, host, goal, timeline, date, kind);
        if (entry == null) {
            addRow(b, getString(R.string.day_detail_nothing), "");
            b.note.setVisibility(View.GONE);
            return;
        }
        addRow(b, getString(R.string.day_detail_target),
                Formats.amountWithUnit(c, entry.scheduledTarget(), goal.unit(), goal.customUnitLabel()));
        if (entry.actualValue().isPositive()) {
            addRow(b, getString(R.string.day_detail_logged),
                    Formats.amountWithUnit(c, entry.actualValue(), goal.unit(), goal.customUnitLabel()));
        }
        if (entry.progressionLevel() != null) {
            addRow(b, getString(R.string.day_detail_level), String.valueOf(entry.progressionLevel()));
        }
        if (entry.skipReason() != null) {
            addRow(b, getString(R.string.day_detail_reason), getString(GoalVisuals.skipReason(entry.skipReason())));
        }
        if (entry.note() != null && !entry.note().isBlank()) {
            b.note.setText(entry.note());
            b.note.setContentDescription(getString(R.string.detail_note_label) + ": " + entry.note());
        } else {
            b.note.setVisibility(View.GONE);
        }
    }

    private void bindActions(SheetDayDetailBinding b, Host host, Goal goal, GoalTimeline timeline, LocalDate date,
                             DayKind kind) {
        boolean open = goal.status().isOpen();
        boolean canMark = open && (kind == DayKind.MISSED || kind == DayKind.PARTIAL || kind == DayKind.SKIPPED
                || kind == DayKind.TODAY_PENDING);
        boolean completed = kind.isCompletion();
        // In a progression goal, a later completion built on this day's target; clearing this one
        // would rewind the plan, so it is only allowed for the most recent advancing day.
        boolean locked = completed && goal.isProgression() && laterAdvanced(timeline, date);
        boolean canClear = open && completed && !locked;

        if (kind == DayKind.MISSED && canMark) {
            b.hint.setVisibility(View.VISIBLE);
            b.hint.setText(R.string.day_detail_missed_hint);
        } else if (open && locked) {
            b.hint.setVisibility(View.VISIBLE);
            b.hint.setText(R.string.day_detail_clear_locked);
        }
        b.markDone.setVisibility(canMark ? View.VISIBLE : View.GONE);
        b.clearDay.setVisibility(canClear ? View.VISIBLE : View.GONE);
        b.markDone.setOnClickListener(v -> {
            Haptics.confirm(v);
            host.hostMarkDone(date);
            dismissAllowingStateLoss();
        });
        b.clearDay.setOnClickListener(v -> {
            Haptics.soft(v);
            host.hostClearDay(date);
            dismissAllowingStateLoss();
        });
    }

    private static boolean laterAdvanced(GoalTimeline timeline, LocalDate date) {
        for (ProgressEntry e : timeline.entriesByDate.values()) {
            if (e.date().isAfter(date) && e.advancedProgression()) return true;
        }
        return false;
    }

    private void addRow(SheetDayDetailBinding b, String key, String value) {
        ItemKeyValueBinding row = ItemKeyValueBinding.inflate(getLayoutInflater(), b.rows, true);
        row.key.setText(key);
        row.value.setText(value);
    }
}
