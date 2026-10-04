package com.app.winterarc.ui.stats;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.engine.DayKind;
import com.app.winterarc.domain.engine.GoalTimeline;
import com.app.winterarc.domain.engine.Heatmap;
import com.app.winterarc.domain.engine.ProgressionEngine;
import com.app.winterarc.domain.engine.StatsCalculator;
import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.MilestoneRepository;
import com.app.winterarc.domain.repository.ProgressRepository;
import com.app.winterarc.ui.common.Combiner;
import com.app.winterarc.ui.common.Nav;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/** A year of history, full statistics, monthly breakdown and milestone history for one goal. */
@HiltViewModel
public class StatsViewModel extends ViewModel {
    public static final int YEAR_WEEKS = 53;

    public record MonthCount(YearMonth month, int completedDays) {}

    public record StatsState(Goal goal, LocalDate today, StatsCalculator.GoalStats stats, Heatmap heatmap,
                             List<MonthCount> months, List<Milestone> milestones, @Nullable Float progressionFraction,
                             GoalTimeline timeline) {}

    private final TimeProvider time;
    private final Combiner<StatsState> state;
    private volatile Goal goal;
    private volatile List<ProgressEntry> entries;
    private volatile List<PausePeriod> pauses;
    private volatile List<Milestone> milestones;
    private volatile DayOfWeek firstDay = DayOfWeek.MONDAY;

    @Inject
    public StatsViewModel(SavedStateHandle saved, GoalRepository goals, ProgressRepository progress,
                          MilestoneRepository milestoneRepository, TimeProvider time, AppExecutors executors) {
        Long id = saved.get(Nav.ARG_GOAL_ID);
        long goalId = id == null ? -1 : id;
        this.time = time;
        state = new Combiner<>(executors, this::compute);
        state.watch(goals.observeGoal(goalId), g -> goal = g);
        state.watch(progress.observeEntries(goalId), v -> entries = v);
        state.watch(progress.observePauses(goalId), v -> pauses = v);
        state.watch(milestoneRepository.observeMilestones(goalId), v -> milestones = v);
    }

    public LiveData<StatsState> state() {
        return state;
    }

    public void setFirstDayOfWeek(DayOfWeek day) {
        firstDay = day;
        state.recompute();
    }

    @Nullable
    private StatsState compute() {
        Goal g = goal;
        List<ProgressEntry> e = entries;
        List<PausePeriod> p = pauses;
        List<Milestone> m = milestones;
        if (g == null || e == null || p == null || m == null) return null;
        LocalDate today = time.today();
        GoalTimeline timeline = GoalTimeline.of(g, e, p);
        boolean pending = false;
        for (Milestone milestone : m) pending |= milestone.celebrationState() == CelebrationState.PENDING;
        Float fraction = g.isProgression() && ProgressionEngine.isValid(g.plan())
                ? ProgressionEngine.completedFraction(g.currentTarget(), g.plan(), pending) : null;
        return new StatsState(g, today, StatsCalculator.stats(today, timeline),
                Heatmap.build(today, YEAR_WEEKS, firstDay, timeline), monthly(today, timeline), m, fraction, timeline);
    }

    /** Completed days for each of the last 12 months, oldest first. */
    static List<MonthCount> monthly(LocalDate today, GoalTimeline timeline) {
        List<MonthCount> out = new ArrayList<>();
        YearMonth month = YearMonth.from(today).minusMonths(11);
        for (int i = 0; i < 12; i++) {
            int count = 0;
            for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()) && !d.isAfter(today); d = d.plusDays(1)) {
                DayKind kind = timeline.classify(d, today);
                if (kind.isCompletion()) count++;
            }
            out.add(new MonthCount(month, count));
            month = month.plusMonths(1);
        }
        return out;
    }
}
