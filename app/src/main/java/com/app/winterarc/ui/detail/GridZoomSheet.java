package com.app.winterarc.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.winterarc.databinding.SheetGridZoomBinding;
import com.app.winterarc.domain.engine.GoalTimeline;
import com.app.winterarc.domain.engine.Heatmap;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.ui.common.ContributionGridView;
import com.app.winterarc.ui.common.Haptics;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.time.LocalDate;

/**
 * The goal's history grid at a size you can tap accurately (opened by tapping a small grid).
 * Tapping a square opens {@link DayDetailSheet}, which can mark a missed day as done. The sheet
 * relays the day sheet's host calls to its own parent (the goal detail or stats screen).
 */
public class GridZoomSheet extends BottomSheetDialogFragment implements DayDetailSheet.Host {
    public static final String TAG = "grid_zoom";
    private static final String ARG_FOCUS = "focus";
    private static final String TAG_DAY = "zoom_day";

    /** Implemented by the screen that opens the zoom: supplies the grid and handles day actions. */
    public interface Host extends DayDetailSheet.Host {
        @Nullable Heatmap hostHeatmap();

        @ColorInt int hostGoalColor();
    }

    @Nullable private SheetGridZoomBinding binding;

    /**
     * Where to centre the zoom for a tap on the small grid: the tapped day, or today when the tap
     * landed before the goal started or in the future (nothing to pick there).
     */
    public static LocalDate focusFor(com.app.winterarc.domain.engine.Heatmap.Day tapped, LocalDate today) {
        com.app.winterarc.domain.engine.Heatmap.CellLevel level = tapped.level();
        boolean usable = level != com.app.winterarc.domain.engine.Heatmap.CellLevel.OUTSIDE
                && level != com.app.winterarc.domain.engine.Heatmap.CellLevel.FUTURE;
        return usable ? tapped.date() : today;
    }

    /** @param focus the day to scroll to (the one tapped on the small grid), or null for the latest week */
    public static GridZoomSheet newInstance(@Nullable LocalDate focus) {
        GridZoomSheet sheet = new GridZoomSheet();
        Bundle args = new Bundle();
        if (focus != null) args.putLong(ARG_FOCUS, focus.toEpochDay());
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = SheetGridZoomBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        SheetGridZoomBinding b = binding;
        if (b == null) return;
        b.grid.setLarge(true);
        b.grid.setOnDayClickListener(day -> {
            Haptics.tick(b.grid);
            if (getChildFragmentManager().findFragmentByTag(TAG_DAY) == null) {
                DayDetailSheet.newInstance(day.date()).show(getChildFragmentManager(), TAG_DAY);
            }
        });
        if (!refresh()) {
            dismissAllowingStateLoss();
            return;
        }
        Bundle args = requireArguments();
        LocalDate focus = args.containsKey(ARG_FOCUS) ? LocalDate.ofEpochDay(args.getLong(ARG_FOCUS)) : null;
        b.scroll.post(() -> {
            if (binding == null) return;
            float x = focus == null ? -1 : binding.grid.centerXOf(focus);
            if (x < 0) binding.scroll.fullScroll(View.FOCUS_RIGHT);
            else binding.scroll.scrollTo(Math.max(0, (int) (x - binding.scroll.getWidth() / 2f)), 0);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog dialog) {
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    /** Redraws from the host's latest data (after a day was marked). Returns false if there is none. */
    public boolean refresh() {
        Host host = host();
        SheetGridZoomBinding b = binding;
        if (host == null || b == null) return false;
        Heatmap heatmap = host.hostHeatmap();
        if (heatmap == null) return false;
        int color = host.hostGoalColor();
        b.grid.setData(heatmap, color, ContributionGridView.localeOf(requireContext()));
        b.legend.setGoalColor(color);
        Goal goal = host.hostGoal();
        if (goal != null) b.title.setText(goal.title());
        return true;
    }

    @Nullable
    private Host host() {
        Fragment parent = getParentFragment();
        return parent instanceof Host ? (Host) parent : null;
    }

    // ---- DayDetailSheet.Host: relay to the screen that opened the zoom.

    @Nullable @Override public Goal hostGoal() { Host h = host(); return h == null ? null : h.hostGoal(); }
    @Nullable @Override public GoalTimeline hostTimeline() { Host h = host(); return h == null ? null : h.hostTimeline(); }
    @Override public LocalDate hostToday() { Host h = host(); return h == null ? LocalDate.now() : h.hostToday(); }
    @Override public void hostMarkDone(LocalDate date) { Host h = host(); if (h != null) h.hostMarkDone(date); }
    @Override public void hostClearDay(LocalDate date) { Host h = host(); if (h != null) h.hostClearDay(date); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
