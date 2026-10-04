package com.app.winterarc.ui.detail;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.engine.DayKind;
import com.app.winterarc.domain.engine.GoalTimeline;
import com.app.winterarc.domain.engine.Heatmap;
import com.app.winterarc.domain.engine.ProgressionEngine;
import com.app.winterarc.domain.engine.StatsCalculator;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.CelebrationState;
import com.app.winterarc.domain.model.ContentType;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.Milestone;
import com.app.winterarc.domain.model.MotivationalContent;
import com.app.winterarc.domain.model.PausePeriod;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.domain.model.SkipReason;
import com.app.winterarc.domain.repository.ContentRepository;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.repository.MilestoneRepository;
import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.domain.repository.ProgressRepository;
import com.app.winterarc.domain.usecase.CheckInUseCase;
import com.app.winterarc.domain.usecase.GoalUseCases;
import com.app.winterarc.domain.usecase.MilestoneUseCase;
import com.app.winterarc.ui.common.Combiner;
import com.app.winterarc.ui.common.Event;
import com.app.winterarc.ui.common.Nav;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class GoalDetailViewModel extends ViewModel {

    private static final String KEY_CELEBRATION_SHOWN = "celebration_shown";

    public record ProgressionInfo(int level, int totalSteps, float fraction, int remaining, List<Amount> steps) {}

    public record DetailState(Goal goal, LocalDate today, DayKind todayKind, @Nullable ProgressEntry todayEntry,
                              StatsCalculator.GoalStats stats, Heatmap heatmap, @Nullable ProgressionInfo progression,
                              @Nullable Milestone pendingMilestone, @Nullable MotivationalContent content,
                              GoalTimeline timeline) {}

    /** One-shot messages for snackbars. */
    public enum Message { COMPLETED, PARTIAL, SKIPPED, UNDONE, NOTE_SAVED, PAUSED, RESUMED, ARCHIVED, RESTORED,
        MAINTAINED, CONTINUED, GOAL_COMPLETED, ERROR }

    private final long goalId;
    private final SavedStateHandle saved;
    private final TimeProvider time;
    private final AppExecutors executors;
    private final CheckInUseCase checkIn;
    private final GoalUseCases goalUseCases;
    private final MilestoneUseCase milestoneUseCase;
    private final ContentRepository content;
    private final PreferencesRepository preferences;
    private final MilestoneRepository milestoneRepository;

    private final Combiner<DetailState> state;
    private final MutableLiveData<Boolean> notFound = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();
    /** Value shown in the CONTINUED message; captured before LiveData reflects the new plan. */
    @Nullable private volatile Amount lastContinuedFinal;
    private final MutableLiveData<Event<Milestone>> celebrate = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> closeScreen = new MutableLiveData<>();

    private volatile Goal goal;
    private volatile boolean goalLoaded;
    private volatile List<ProgressEntry> entries;
    private volatile List<PausePeriod> pauses;
    private volatile List<Milestone> milestones;
    private volatile int weeks = 20;
    private volatile DayOfWeek firstDay = DayOfWeek.MONDAY;

    @Inject
    public GoalDetailViewModel(SavedStateHandle saved, GoalRepository goals, ProgressRepository progress,
                               MilestoneRepository milestoneRepository, CheckInUseCase checkIn,
                               GoalUseCases goalUseCases, MilestoneUseCase milestoneUseCase,
                               ContentRepository content, PreferencesRepository preferences,
                               TimeProvider time, AppExecutors executors) {
        Long id = saved.get(Nav.ARG_GOAL_ID);
        this.goalId = id == null ? -1 : id;
        this.saved = saved;
        this.time = time;
        this.executors = executors;
        this.checkIn = checkIn;
        this.goalUseCases = goalUseCases;
        this.milestoneUseCase = milestoneUseCase;
        this.content = content;
        this.preferences = preferences;
        this.milestoneRepository = milestoneRepository;
        state = new Combiner<>(executors, this::compute);
        state.watch(goals.observeGoal(goalId), g -> {
            goal = g;
            goalLoaded = true;
            if (g == null) notFound.setValue(true);
        });
        state.watch(progress.observeEntries(goalId), v -> entries = v);
        state.watch(progress.observePauses(goalId), v -> pauses = v);
        state.watch(milestoneRepository.observeMilestones(goalId), v -> milestones = v);
    }

    public long goalId() {
        return goalId;
    }

    public LiveData<DetailState> state() {
        return state;
    }

    public LiveData<Boolean> notFound() {
        return notFound;
    }

    public LiveData<Event<Message>> messages() {
        return messages;
    }

    public LiveData<Event<Milestone>> celebrate() {
        return celebrate;
    }

    public LiveData<Event<Boolean>> closeScreen() {
        return closeScreen;
    }

    @Nullable
    public Amount lastContinuedFinal() {
        return lastContinuedFinal;
    }

    public boolean hapticsEnabled() {
        return preferences.get().hapticsEnabled();
    }

    /** Sets how many week columns fit on screen; recomputes the grid. */
    public void configureGrid(int weekCount, DayOfWeek firstDayOfWeek) {
        if (weekCount == weeks && firstDayOfWeek == firstDay) {
            state.recompute(); // still refresh "today" on resume
            return;
        }
        weeks = weekCount;
        firstDay = firstDayOfWeek;
        state.recompute();
    }

    @Nullable
    private DetailState compute() {
        Goal g = goal;
        List<ProgressEntry> e = entries;
        List<PausePeriod> p = pauses;
        List<Milestone> m = milestones;
        if (!goalLoaded || g == null || e == null || p == null || m == null) return null;
        LocalDate today = time.today();
        GoalTimeline timeline = GoalTimeline.of(g, e, p);
        StatsCalculator.GoalStats stats = StatsCalculator.stats(today, timeline);
        Heatmap heatmap = Heatmap.build(today, weeks, firstDay, timeline);
        Milestone pending = null;
        for (Milestone milestone : m) {
            if (milestone.celebrationState() == CelebrationState.PENDING) pending = milestone;
        }
        ProgressionInfo info = null;
        if (g.isProgression() && ProgressionEngine.isValid(g.plan())) {
            boolean reached = pending != null;
            info = new ProgressionInfo(
                    ProgressionEngine.level(g.currentTarget(), g.plan()),
                    ProgressionEngine.totalSteps(g.plan()),
                    ProgressionEngine.completedFraction(g.currentTarget(), g.plan(), reached),
                    ProgressionEngine.remainingCompletions(g.currentTarget(), g.plan(), reached),
                    ProgressionEngine.steps(g.plan()));
        }
        Amount km = g.unit().toKilometers(g.currentTarget());
        MotivationalContent pick = content.pick(g.activityType(), km,
                EnumSet.of(ContentType.FACT, ContentType.QUOTE, ContentType.ENCOURAGEMENT),
                (int) (today.toEpochDay() + g.id()));
        return new DetailState(g, today, timeline.classify(today, today), timeline.entriesByDate.get(today), stats,
                heatmap, info, pending, pick, timeline);
    }

    /** Shows the celebration once per screen visit when a milestone is waiting for a decision. */
    public void maybeAutoCelebrate(DetailState s) {
        if (s.pendingMilestone() == null || Boolean.TRUE.equals(saved.get(KEY_CELEBRATION_SHOWN))) return;
        saved.set(KEY_CELEBRATION_SHOWN, true);
        celebrate.setValue(new Event<>(s.pendingMilestone()));
    }

    public void openCelebration() {
        DetailState s = state.getValue();
        if (s != null && s.pendingMilestone() != null) celebrate.setValue(new Event<>(s.pendingMilestone()));
    }

    // ---- Check-in ---------------------------------------------------------------------------

    public void complete() {
        runCheckIn(() -> checkIn.complete(goalId, time.today()));
    }

    public void logValue(Amount amount, @Nullable String note) {
        runCheckIn(() -> checkIn.logValue(goalId, time.today(), amount, note));
    }

    public void skip(SkipReason reason, @Nullable String note) {
        runCheckIn(() -> checkIn.skip(goalId, time.today(), reason, note));
    }

    public void saveNote(String note) {
        executors.io().execute(() -> {
            CheckInUseCase.Result r = checkIn.saveNote(goalId, time.today(), note);
            post(r.status() == CheckInUseCase.Status.SAVED ? Message.NOTE_SAVED : Message.ERROR);
        });
    }

    public void undoToday() {
        executors.io().execute(() -> {
            CheckInUseCase.Result r = checkIn.undo(goalId, time.today());
            post(r.status() == CheckInUseCase.Status.SAVED ? Message.UNDONE : Message.ERROR);
        });
    }

    private void runCheckIn(Supplier<CheckInUseCase.Result> action) {
        executors.io().execute(() -> {
            CheckInUseCase.Result r = action.get();
            executors.main().execute(() -> {
                if (r.status() != CheckInUseCase.Status.SAVED || r.entry() == null) {
                    messages.setValue(new Event<>(Message.ERROR));
                    return;
                }
                if (r.milestoneReached()) {
                    saved.set(KEY_CELEBRATION_SHOWN, true);
                    // Read the new milestone directly; LiveData may not have delivered it yet.
                    executors.io().execute(() -> {
                        Milestone pending = milestoneRepository.getPending(goalId);
                        if (pending != null) executors.main().execute(() -> celebrate.setValue(new Event<>(pending)));
                    });
                    return;
                }
                switch (r.entry().status()) {
                    case COMPLETED: messages.setValue(new Event<>(Message.COMPLETED)); break;
                    case PARTIAL: messages.setValue(new Event<>(Message.PARTIAL)); break;
                    case SKIPPED: messages.setValue(new Event<>(Message.SKIPPED)); break;
                    default: messages.setValue(new Event<>(Message.NOTE_SAVED)); break;
                }
            });
        });
    }

    // ---- Lifecycle --------------------------------------------------------------------------

    public void pause() {
        executors.io().execute(() -> {
            goalUseCases.pause(goalId);
            post(Message.PAUSED);
        });
    }

    public void resume() {
        executors.io().execute(() -> {
            goalUseCases.resume(goalId);
            post(Message.RESUMED);
        });
    }

    public void archive() {
        executors.io().execute(() -> {
            goalUseCases.archive(goalId);
            executors.main().execute(() -> closeScreen.setValue(new Event<>(true)));
        });
    }

    public void restore() {
        executors.io().execute(() -> {
            goalUseCases.restore(goalId);
            post(Message.RESTORED);
        });
    }

    // ---- Milestone decisions ----------------------------------------------------------------

    public void maintain() {
        executors.io().execute(() -> post(milestoneUseCase.maintain(goalId) ? Message.MAINTAINED : Message.ERROR));
    }

    public void continueProgressing(Amount newFinal, @Nullable Amount hop) {
        lastContinuedFinal = newFinal;
        executors.io().execute(() ->
                post(milestoneUseCase.continueProgressing(goalId, newFinal, hop) ? Message.CONTINUED : Message.ERROR));
    }

    public void markComplete() {
        executors.io().execute(() -> {
            boolean ok = milestoneUseCase.markComplete(goalId);
            post(ok ? Message.GOAL_COMPLETED : Message.ERROR);
        });
    }

    private void post(Message m) {
        executors.main().execute(() -> messages.setValue(new Event<>(m)));
    }
}
