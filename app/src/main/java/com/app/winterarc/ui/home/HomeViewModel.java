package com.app.winterarc.ui.home;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.engine.DayKind;
import com.app.winterarc.domain.engine.GoalTimeline;
import com.app.winterarc.domain.engine.Heatmap;
import com.app.winterarc.domain.engine.ProgressionEngine;
import com.app.winterarc.domain.engine.StatsCalculator;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.MilestoneRepository;
import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.domain.repository.ProgressRepository;
import com.app.winterarc.domain.usecase.CheckInUseCase;
import com.app.winterarc.domain.usecase.GoalUseCases;
import com.app.winterarc.ui.common.Combiner;
import com.app.winterarc.ui.common.Event;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/** Answers: what do I need to do today, how consistent have I been, what's next. */
@HiltViewModel
public class HomeViewModel extends ViewModel {

    public static final int PREVIEW_WEEKS = 22;

    public enum Greeting { MORNING, AFTERNOON, EVENING }

    public enum Section { TODAY, REST, PAUSED }

    /** Everything one goal card needs. */
    public record GoalCard(Goal goal, Section section, DayKind todayKind, @Nullable ProgressEntry todayEntry,
                           int currentStreak, Heatmap preview, boolean milestonePending,
                           float progressionFraction) {}

    public record HomeState(Greeting greeting, LocalDate today, int remainingToday, int scheduledToday,
                            List<GoalCard> cards) {}

    /** A snackbar request; {@code undoGoalId > 0} offers an Undo action. */
    public record Message(int kind, long undoGoalId, @Nullable String goalTitle) {
        public static final int COMPLETED = 1;
        public static final int MILESTONE = 2;
        public static final int ERROR = 3;
    }

    private final TimeProvider time;
    private final AppExecutors executors;
    private final CheckInUseCase checkIn;
    private final GoalUseCases goalUseCases;
    private final PreferencesRepository preferences;
    private final Combiner<HomeState> state;
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();

    private volatile List<Goal> goals;
    private volatile List<ProgressEntry> entries;
    private volatile List<PausePeriod> pauses;
    private volatile List<Milestone> pending;
    private DayOfWeek firstDayOfWeek = DayOfWeek.MONDAY;

    @Inject
    public HomeViewModel(GoalRepository goalRepository, ProgressRepository progress, MilestoneRepository milestones,
                         CheckInUseCase checkIn, GoalUseCases goalUseCases, PreferencesRepository preferences,
                         TimeProvider time, AppExecutors executors) {
        this.time = time;
        this.executors = executors;
        this.checkIn = checkIn;
        this.goalUseCases = goalUseCases;
        this.preferences = preferences;
        state = new Combiner<>(executors, this::compute);
        state.watch(goalRepository.observeGoals(EnumSet.of(GoalStatus.ACTIVE, GoalStatus.PAUSED)), v -> goals = v);
        state.watch(progress.observeEntriesSince(LocalDate.of(1970, 1, 1)), v -> entries = v);
        state.watch(progress.observeAllPauses(), v -> pauses = v);
        state.watch(milestones.observePending(), v -> pending = v);
    }

    public LiveData<HomeState> state() {
        return state;
    }

    public LiveData<Event<Message>> messages() {
        return messages;
    }

    public boolean hapticsEnabled() {
        return preferences.get().hapticsEnabled();
    }

    /** Called on resume so "today" stays correct if the app was open across midnight. */
    public void refresh(DayOfWeek firstDay) {
        firstDayOfWeek = firstDay;
        state.recompute();
    }

    @Nullable
    private HomeState compute() {
        List<Goal> g = goals;
        List<ProgressEntry> e = entries;
        List<PausePeriod> p = pauses;
        List<Milestone> m = pending;
        if (g == null || e == null || p == null || m == null) return null;
        return build(time.today(), time.zonedNow().toLocalTime(), firstDayOfWeek, g, e, p, m);
    }

    /** Pure state assembly; visible for tests. */
    static HomeState build(LocalDate today, LocalTime now, DayOfWeek firstDay, List<Goal> goals,
                           List<ProgressEntry> entries, List<PausePeriod> pauses, List<Milestone> pending) {
        Map<Long, List<ProgressEntry>> entriesByGoal = new HashMap<>();
        for (ProgressEntry e : entries) entriesByGoal.computeIfAbsent(e.goalId(), k -> new ArrayList<>()).add(e);
        Map<Long, List<PausePeriod>> pausesByGoal = new HashMap<>();
        for (PausePeriod p : pauses) pausesByGoal.computeIfAbsent(p.goalId(), k -> new ArrayList<>()).add(p);
        Set<Long> pendingGoals = new HashSet<>();
        for (Milestone m : pending) pendingGoals.add(m.goalId());

        List<GoalCard> cards = new ArrayList<>();
        int remaining = 0;
        int scheduled = 0;
        for (Goal goal : goals) {
            List<ProgressEntry> ge = entriesByGoal.getOrDefault(goal.id(), Collections.emptyList());
            GoalTimeline timeline = GoalTimeline.of(goal, ge, pausesByGoal.getOrDefault(goal.id(), Collections.emptyList()));
            DayKind todayKind = timeline.classify(today, today);
            Section section;
            if (goal.status() == GoalStatus.PAUSED) {
                section = Section.PAUSED;
            } else if (goal.schedule().isScheduled(today) && !today.isBefore(goal.startDate())) {
                section = Section.TODAY;
                scheduled++;
                if (todayKind == DayKind.TODAY_PENDING || todayKind == DayKind.PARTIAL) remaining++;
            } else {
                section = Section.REST;
            }
            StatsCalculator.StreakSummary streak = StatsCalculator.streaks(today, timeline);
            boolean milestone = pendingGoals.contains(goal.id());
            float fraction = goal.isProgression() ? ProgressionEngine.completedFraction(goal.currentTarget(), goal.plan(), milestone) : 0f;
            cards.add(new GoalCard(goal, section, todayKind, timeline.entriesByDate.get(today), streak.current(),
                    Heatmap.build(today, PREVIEW_WEEKS, firstDay, timeline), milestone, fraction));
        }
        // Today's pending targets first, then done-today, then rest days, then paused.
        cards.sort((a, b) -> {
            int s = Integer.compare(a.section().ordinal(), b.section().ordinal());
            if (s != 0) return s;
            boolean aDone = a.todayKind().isCompletion() || a.todayKind() == DayKind.SKIPPED;
            boolean bDone = b.todayKind().isCompletion() || b.todayKind() == DayKind.SKIPPED;
            return Boolean.compare(aDone, bDone);
        });
        Greeting greeting = now.getHour() < 12 ? Greeting.MORNING : now.getHour() < 18 ? Greeting.AFTERNOON : Greeting.EVENING;
        return new HomeState(greeting, today, remaining, scheduled, cards);
    }

    public void complete(Goal goal) {
        executors.io().execute(() -> {
            CheckInUseCase.Result r = checkIn.complete(goal.id(), time.today());
            executors.main().execute(() -> {
                if (r.status() != CheckInUseCase.Status.SAVED) {
                    messages.setValue(new Event<>(new Message(Message.ERROR, 0, null)));
                } else {
                    messages.setValue(new Event<>(new Message(r.milestoneReached() ? Message.MILESTONE : Message.COMPLETED,
                            goal.id(), goal.title())));
                }
            });
        });
    }

    public void undo(long goalId) {
        executors.io().execute(() -> checkIn.undo(goalId, time.today()));
    }

    public void resume(long goalId) {
        executors.io().execute(() -> goalUseCases.resume(goalId));
    }
}
