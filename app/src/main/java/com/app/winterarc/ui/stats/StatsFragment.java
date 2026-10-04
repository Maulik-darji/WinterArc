package com.app.winterarc.ui.stats;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.FragmentStatsBinding;
import com.app.winterarc.databinding.ItemKeyValueBinding;
import com.app.winterarc.databinding.ItemMonthRowBinding;
import com.app.winterarc.domain.engine.GoalTimeline;
import com.app.winterarc.domain.engine.StatsCalculator;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.ui.common.ContributionGridView;
import com.app.winterarc.ui.common.GoalVisuals;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.detail.DayDetailSheet;
import com.app.winterarc.ui.detail.GoalDetailFragment;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class StatsFragment extends Fragment implements DayDetailSheet.Host {
    private FragmentStatsBinding binding;
    private StatsViewModel viewModel;
    @Nullable private StatsViewModel.StatsState current;
    private boolean scrolledToEnd;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(StatsViewModel.class);
        Ui.applySystemBarPadding(binding.statsRoot, true, false);
        Ui.applySystemBarPadding(binding.scroll, false, true);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));
        binding.grid.setOnDayClickListener(day -> {
            if (getChildFragmentManager().findFragmentByTag("day") == null) {
                DayDetailSheet.newInstance(day.date()).show(getChildFragmentManager(), "day");
            }
        });
        viewModel.setFirstDayOfWeek(WeekFields.of(Formats.locale(requireContext())).getFirstDayOfWeek());
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(StatsViewModel.StatsState s) {
        current = s;
        Context c = requireContext();
        Goal goal = s.goal();
        int color = GoalVisuals.color(c, goal.color());
        binding.goalTitle.setText(goal.title());
        binding.grid.setData(s.heatmap(), color, ContributionGridView.localeOf(c));
        binding.legend.setGoalColor(color);
        if (!scrolledToEnd) {
            // Most recent weeks are on the right; start there.
            binding.gridScroll.post(() -> binding.gridScroll.fullScroll(View.FOCUS_RIGHT));
            scrolledToEnd = true;
        }

        StatsCalculator.GoalStats st = s.stats();
        GoalDetailFragment.tile(binding.stats.statCurrent, days(st.currentStreak()), R.string.stat_current_streak);
        GoalDetailFragment.tile(binding.stats.statLongest, days(st.longestStreak()), R.string.stat_longest_streak);
        GoalDetailFragment.tile(binding.stats.statCompleted, String.valueOf(st.completedDays()), R.string.stat_completed_days);
        GoalDetailFragment.tile(binding.stats.statRate, Formats.percent(c, st.completionRate()), R.string.stat_completion_rate);
        GoalDetailFragment.tile(binding.stats.statTotal,
                Formats.amountWithUnit(c, st.totalValue(), goal.unit(), goal.customUnitLabel()), R.string.stat_total);
        if (s.progressionFraction() != null) {
            GoalDetailFragment.tile(binding.stats.statExtra, Formats.percent(c, s.progressionFraction()), R.string.stat_toward_final);
        } else {
            GoalDetailFragment.tile(binding.stats.statExtra, String.valueOf(st.exceededDays()), R.string.stat_exceeded);
        }

        binding.breakdown.removeAllViews();
        row(binding.breakdown, getString(R.string.stat_exceeded), days(st.exceededDays()));
        row(binding.breakdown, getString(R.string.stat_partial), days(st.partialDays()));
        row(binding.breakdown, getString(R.string.stat_rest), days(st.skippedDays()));

        binding.months.removeAllViews();
        int max = 1;
        for (StatsViewModel.MonthCount m : s.months()) max = Math.max(max, m.completedDays());
        for (StatsViewModel.MonthCount m : s.months()) {
            ItemMonthRowBinding row = ItemMonthRowBinding.inflate(getLayoutInflater(), binding.months, true);
            String name = m.month().getMonth().getDisplayName(TextStyle.SHORT, Formats.locale(c));
            row.month.setText(name);
            row.count.setText(String.valueOf(m.completedDays()));
            row.bar.setIndicatorColor(color);
            row.bar.setMax(max);
            row.bar.setProgressCompat(m.completedDays(), false);
            row.getRoot().setContentDescription(getResources().getQuantityString(R.plurals.stats_month_value,
                    m.completedDays(), m.month().getMonth().getDisplayName(TextStyle.FULL, Formats.locale(c)),
                    m.completedDays()));
        }

        binding.milestones.removeAllViews();
        if (s.milestones().isEmpty()) {
            row(binding.milestones, getString(R.string.stats_no_milestones), "");
        }
        for (Milestone m : s.milestones()) {
            int state;
            switch (m.celebrationState()) {
                case MAINTAINED: state = R.string.stats_milestone_maintained; break;
                case CONTINUED: state = R.string.stats_milestone_continued; break;
                case COMPLETED: state = R.string.stats_milestone_completed; break;
                default: state = R.string.stats_milestone_pending; break;
            }
            row(binding.milestones, getString(R.string.stats_milestone_item,
                            Formats.amountWithUnit(c, m.targetValue(), goal.unit(), goal.customUnitLabel()),
                            Formats.date(c, m.achievedDate())),
                    getString(state));
        }
    }

    private void row(ViewGroup parent, String key, String value) {
        ItemKeyValueBinding row = ItemKeyValueBinding.inflate(getLayoutInflater(), parent, true);
        row.key.setText(key);
        row.value.setText(value);
    }

    private String days(int count) {
        return getResources().getQuantityString(R.plurals.stat_days_value, count, count);
    }

    @Nullable
    @Override
    public Goal hostGoal() {
        return current == null ? null : current.goal();
    }

    @Nullable
    @Override
    public GoalTimeline hostTimeline() {
        return current == null ? null : current.timeline();
    }

    @Override
    public LocalDate hostToday() {
        return current == null ? LocalDate.now() : current.today();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
