package com.app.winterarc.ui.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.domain.engine.GoalValidator;
import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalDraft;
import com.app.winterarc.domain.model.TrackingMode;
import com.app.winterarc.domain.usecase.GoalUseCases;
import com.app.winterarc.testing.FakeTimeProvider;
import com.app.winterarc.testing.InMemoryRepositories;
import com.app.winterarc.testing.TestGoals;
import com.app.winterarc.ui.common.Nav;

import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Map;

public class GoalEditorViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private final InMemoryRepositories.Goals goals = new InMemoryRepositories.Goals();
    private final InMemoryRepositories.Progress progress = new InMemoryRepositories.Progress();
    private final FakeTimeProvider time = new FakeTimeProvider(LocalDate.of(2026, 10, 2));

    private GoalEditorViewModel create(SavedStateHandle handle) {
        GoalUseCases useCases = new GoalUseCases(goals, progress, new InMemoryRepositories.DirectTransactions(),
                new InMemoryRepositories.RecordingReminders(), time, Analytics.NO_OP);
        return new GoalEditorViewModel(handle, goals, useCases, AppExecutors.direct(), time, Analytics.NO_OP);
    }

    @Test
    public void fullCreationFlowSavesProgressionGoal() {
        GoalEditorViewModel vm = create(new SavedStateHandle());
        vm.selectActivity(ActivityType.RUNNING);
        assertTrue(vm.next());
        vm.selectMode(TrackingMode.PROGRESSION);
        assertTrue(vm.next());
        vm.chooseSuggestedFinal(TestGoals.km(21.1));
        assertTrue(vm.next());
        // Details: title required
        assertFalse(vm.next());
        assertTrue(vm.state().getValue().errors().contains(GoalValidator.Error.TITLE_BLANK));
        vm.setTitle("Half marathon");
        assertTrue(vm.next());
        assertTrue(vm.next()); // schedule defaults are valid
        assertEquals(GoalEditorViewModel.STEP_REVIEW, vm.state().getValue().step());

        vm.save();
        Long id = vm.saved().getValue().consume();
        assertNotNull(id);
        Goal saved = goals.getGoal(id);
        assertEquals("Half marathon", saved.title());
        assertEquals(TrackingMode.PROGRESSION, saved.trackingMode());
        assertEquals(TestGoals.km(21.1), saved.plan().finalTarget());
        assertEquals(TestGoals.km(1), saved.currentTarget());
    }

    @Test
    public void invalidPlanBlocksTheTargetStep() {
        GoalEditorViewModel vm = create(new SavedStateHandle());
        vm.selectActivity(ActivityType.WALKING);
        vm.next();
        vm.selectMode(TrackingMode.PROGRESSION);
        vm.next();
        vm.setFinalTarget(TestGoals.km(1));
        vm.setStartTarget(TestGoals.km(2));
        assertFalse(vm.next());
        assertTrue(vm.state().getValue().errors().contains(GoalValidator.Error.FINAL_NOT_GREATER_THAN_START));
    }

    @Test
    public void aggressivePlanRequiresConfirmationBeforeSaving() {
        GoalEditorViewModel vm = create(new SavedStateHandle());
        vm.selectActivity(ActivityType.RUNNING);
        vm.selectMode(TrackingMode.PROGRESSION);
        vm.setFinalTarget(TestGoals.km(40));
        vm.setHop(TestGoals.km(8));
        vm.setTitle("Too fast");
        vm.jumpTo(GoalEditorViewModel.STEP_REVIEW);
        vm.save();
        assertNotNull(vm.confirmSafety().getValue().consume());
        assertTrue(goals.rows.isEmpty());
        vm.acknowledgeSafety();
        assertEquals(1, goals.rows.size());
    }

    @Test
    public void draftSurvivesProcessDeathViaSavedState() {
        SavedStateHandle handle = new SavedStateHandle();
        GoalEditorViewModel vm = create(handle);
        vm.selectActivity(ActivityType.READING);
        vm.next();
        vm.setTitle("Read daily");
        // Simulate restoration: a new ViewModel built from the same saved values.
        SavedStateHandle restored = new SavedStateHandle(Map.of(
                "draft", (Object) handle.get("draft"), "step", (Object) handle.get("step")));
        GoalEditorViewModel again = create(restored);
        GoalDraft d = again.state().getValue().draft();
        assertEquals("Read daily", d.title);
        assertEquals(ActivityType.READING, d.activityType);
        assertEquals(GoalEditorViewModel.STEP_MODE, again.state().getValue().step());
    }

    @Test
    public void editModeLoadsExistingGoalAndKeepsProgress() {
        long id = goals.insertGoal(TestGoals.progression(TestGoals.km(1), TestGoals.km(10), TestGoals.km(1), time.today())
                .toBuilder().currentTarget(TestGoals.km(6)).build());
        SavedStateHandle handle = new SavedStateHandle(Map.of(Nav.ARG_GOAL_ID, (Object) id));
        GoalEditorViewModel vm = create(handle);
        assertTrue(vm.isEditing());
        assertEquals(GoalEditorViewModel.STEP_REVIEW, vm.state().getValue().step());
        vm.setFinalTarget(TestGoals.km(15));
        vm.save();
        assertEquals(TestGoals.km(6), goals.getGoal(id).currentTarget());
        assertEquals(TestGoals.km(15), goals.getGoal(id).plan().finalTarget());
    }
}
