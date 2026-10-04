package com.app.winterarc.ui.editor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.AnalyticsEvent;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.domain.engine.GoalValidator;
import com.app.winterarc.domain.engine.SafetyAdvisor;
import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalColor;
import com.app.winterarc.domain.model.GoalDraft;
import com.app.winterarc.domain.model.GoalIcon;
import com.app.winterarc.domain.model.GoalUnit;
import com.app.winterarc.domain.model.ReminderConfig;
import com.app.winterarc.domain.model.TrackingMode;
import com.app.winterarc.domain.model.WeeklySchedule;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.usecase.GoalUseCases;
import com.app.winterarc.ui.common.Event;
import com.app.winterarc.ui.common.Nav;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * State holder for the create/edit wizard. The draft and current step live in SavedStateHandle,
 * so the wizard survives rotation and process death.
 */
@HiltViewModel
public class GoalEditorViewModel extends ViewModel {

    public static final int STEP_ACTIVITY = 0;
    public static final int STEP_MODE = 1;
    public static final int STEP_TARGET = 2;
    public static final int STEP_DETAILS = 3;
    public static final int STEP_SCHEDULE = 4;
    public static final int STEP_REVIEW = 5;
    public static final int STEP_COUNT = 6;

    private static final String KEY_DRAFT = "draft";
    private static final String KEY_STEP = "step";
    private static final String KEY_SAFETY_ACK = "safety_ack";
    private static final String KEY_LOADED = "loaded";
    private static final String KEY_CUSTOM_FINAL = "custom_final";

    public record UiState(GoalDraft draft, int step, boolean editing, Set<GoalValidator.Error> errors,
                          SafetyAdvisor.Assessment safety, boolean saving, boolean loading,
                          boolean customFinal) {}

    private final SavedStateHandle state;
    private final GoalRepository goals;
    private final GoalUseCases useCases;
    private final AppExecutors executors;
    private final Analytics analytics;
    private final long goalId;

    private final MutableLiveData<UiState> ui = new MutableLiveData<>();
    private final MutableLiveData<Event<Long>> saved = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> confirmSafety = new MutableLiveData<>();
    private Set<GoalValidator.Error> errors = Collections.emptySet();
    private boolean saving;
    private boolean loading;

    @Inject
    public GoalEditorViewModel(SavedStateHandle state, GoalRepository goals, GoalUseCases useCases,
                               AppExecutors executors, TimeProvider time, Analytics analytics) {
        this.state = state;
        this.goals = goals;
        this.useCases = useCases;
        this.executors = executors;
        this.analytics = analytics;
        Long id = state.get(Nav.ARG_GOAL_ID);
        goalId = id == null ? -1 : id;

        if (state.get(KEY_DRAFT) == null) {
            state.set(KEY_DRAFT, GoalDraft.forActivity(ActivityType.WALKING, time.today()));
            state.set(KEY_STEP, isEditing() ? STEP_REVIEW : STEP_ACTIVITY);
            if (!isEditing()) analytics.track(AnalyticsEvent.of(AnalyticsEvent.GOAL_CREATION_STARTED));
        }
        if (isEditing() && !Boolean.TRUE.equals(state.get(KEY_LOADED))) {
            loading = true;
            executors.io().execute(() -> {
                Goal goal = goals.getGoal(goalId);
                executors.main().execute(() -> {
                    loading = false;
                    if (goal != null) {
                        state.set(KEY_DRAFT, GoalDraft.fromGoal(goal));
                        state.set(KEY_LOADED, true);
                    }
                    publish();
                });
            });
        }
        publish();
    }

    public LiveData<UiState> state() {
        return ui;
    }

    public LiveData<Event<Long>> saved() {
        return saved;
    }

    public LiveData<Event<Boolean>> confirmSafety() {
        return confirmSafety;
    }

    public boolean isEditing() {
        return goalId > 0;
    }

    private GoalDraft draft() {
        return state.get(KEY_DRAFT);
    }

    private int step() {
        Integer s = state.get(KEY_STEP);
        return s == null ? STEP_ACTIVITY : s;
    }

    /** Applies a change to a copy of the draft (so SavedStateHandle sees a new value). */
    private void update(java.util.function.Consumer<GoalDraft> change) {
        GoalDraft d = draft().copy();
        change.accept(d);
        state.set(KEY_DRAFT, d);
        // Clear errors as the user fixes them, without showing new ones until they press Next.
        if (!errors.isEmpty()) {
            Set<GoalValidator.Error> now = errorsForStep(d, step());
            now.retainAll(errors);
            errors = now;
        }
        state.set(KEY_SAFETY_ACK, false);
        publish();
    }

    private void publish() {
        GoalDraft d = draft();
        ui.setValue(new UiState(d, step(), isEditing(), errors, SafetyAdvisor.assess(d), saving, loading,
                Boolean.TRUE.equals(state.get(KEY_CUSTOM_FINAL))));
    }

    // ---- Navigation between steps -----------------------------------------------------------

    /** Advances if the current step is valid; returns false and shows errors otherwise. */
    public boolean next() {
        Set<GoalValidator.Error> stepErrors = errorsForStep(draft(), step());
        errors = stepErrors;
        if (!stepErrors.isEmpty()) {
            publish();
            return false;
        }
        if (step() < STEP_REVIEW) state.set(KEY_STEP, isEditing() ? STEP_REVIEW : step() + 1);
        publish();
        return true;
    }

    /** Returns false when already on the first step (the caller then leaves the wizard). */
    public boolean back() {
        errors = Collections.emptySet();
        if (isEditing() && step() != STEP_REVIEW) {
            state.set(KEY_STEP, STEP_REVIEW);
            publish();
            return true;
        }
        if (step() == STEP_ACTIVITY || (isEditing() && step() == STEP_REVIEW)) return false;
        state.set(KEY_STEP, step() - 1);
        publish();
        return true;
    }

    public void jumpTo(int step) {
        errors = Collections.emptySet();
        state.set(KEY_STEP, step);
        publish();
    }

    static Set<GoalValidator.Error> errorsForStep(GoalDraft d, int step) {
        Set<GoalValidator.Error> all = GoalValidator.validate(d);
        Set<GoalValidator.Error> relevant = EnumSet.noneOf(GoalValidator.Error.class);
        for (GoalValidator.Error e : all) {
            if (step == STEP_REVIEW || stepOf(e) == step) relevant.add(e);
        }
        return relevant;
    }

    static int stepOf(GoalValidator.Error e) {
        switch (e) {
            case TITLE_BLANK:
            case TITLE_TOO_LONG:
            case DESCRIPTION_TOO_LONG:
                return STEP_DETAILS;
            case NO_ACTIVE_DAYS:
                return STEP_SCHEDULE;
            default:
                return STEP_TARGET;
        }
    }

    // ---- Edits ------------------------------------------------------------------------------

    public void selectActivity(ActivityType activity) {
        update(d -> {
            if (isEditing()) {
                d.activityType = activity;
            } else if (d.activityType != activity || d.title.isEmpty()) {
                d.applyActivityDefaults(activity);
            }
        });
    }

    public void selectMode(TrackingMode mode) {
        if (draft().trackingMode != mode) analytics.track(AnalyticsEvent.modeSelected(mode));
        update(d -> d.trackingMode = mode);
    }

    public void setUnit(GoalUnit unit) {
        update(d -> {
            d.unit = unit;
            if (!unit.supportsDecimal()) {
                d.consistencyTarget = roundUp(d.consistencyTarget);
                d.startTarget = roundUp(d.startTarget);
                d.finalTarget = roundUp(d.finalTarget);
                d.hop = roundUp(d.hop);
            }
        });
    }

    private static Amount roundUp(Amount a) {
        long whole = (a.milli() + Amount.SCALE - 1) / Amount.SCALE;
        return Amount.whole(Math.max(1, whole));
    }

    public void setCustomUnitLabel(String label) { update(d -> d.customUnitLabel = label); }
    public void setConsistencyTarget(Amount a) { update(d -> d.consistencyTarget = a); }
    public void setStartTarget(Amount a) { update(d -> d.startTarget = a); }
    public void setFinalTarget(Amount a) { update(d -> d.finalTarget = a); }

    /** Picks a suggested milestone from the carousel. */
    public void chooseSuggestedFinal(Amount a) {
        state.set(KEY_CUSTOM_FINAL, false);
        setFinalTarget(a);
    }

    /** Switches the final target to free entry with the wheel picker, keeping the current value. */
    public void chooseCustomFinal() {
        state.set(KEY_CUSTOM_FINAL, true);
        publish();
    }
    public void setHop(Amount a) { update(d -> d.hop = a); }
    public void setTitle(String title) { update(d -> d.title = title); }
    public void setDescription(String text) { update(d -> d.description = text); }
    public void setIcon(GoalIcon icon) { update(d -> d.icon = icon); }
    public void setColor(GoalColor color) { update(d -> d.color = color); }
    public void setStartDate(LocalDate date) { update(d -> d.startDate = date); }

    public void setTitleIfBlank(String title) {
        if (draft().title.isBlank()) update(d -> d.title = title);
    }

    public void toggleDay(DayOfWeek day, boolean enabled) {
        update(d -> d.schedule = d.schedule.with(day, enabled));
    }

    public void setSchedule(WeeklySchedule schedule) {
        update(d -> d.schedule = schedule);
    }

    public void setReminderEnabled(boolean enabled) {
        update(d -> d.reminder = d.reminder.withEnabled(enabled));
    }

    public void setReminderTime(LocalTime time) {
        update(d -> d.reminder = new ReminderConfig(true, time));
    }

    public void acknowledgeSafety() {
        state.set(KEY_SAFETY_ACK, true);
        save();
    }

    // ---- Save -------------------------------------------------------------------------------

    public void save() {
        if (saving) return;
        GoalDraft d = draft();
        errors = GoalValidator.validate(d);
        if (!errors.isEmpty()) {
            // Jump to the first step with a problem.
            int first = STEP_REVIEW;
            for (GoalValidator.Error e : errors) first = Math.min(first, stepOf(e));
            state.set(KEY_STEP, first);
            publish();
            return;
        }
        if (SafetyAdvisor.assess(d).level() == SafetyAdvisor.Level.CONFIRM
                && !Boolean.TRUE.equals(state.get(KEY_SAFETY_ACK))) {
            confirmSafety.setValue(new Event<>(true));
            return;
        }
        saving = true;
        publish();
        GoalDraft snapshot = d.copy();
        executors.io().execute(() -> {
            GoalUseCases.SaveResult result = isEditing() ? useCases.update(goalId, snapshot) : useCases.create(snapshot);
            executors.main().execute(() -> {
                saving = false;
                if (result.isSaved()) {
                    saved.setValue(new Event<>(result.goalId()));
                } else {
                    errors = result.errors();
                }
                publish();
            });
        });
    }

}
