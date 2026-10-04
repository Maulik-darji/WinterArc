package com.app.winterarc.domain.engine;

import static com.app.winterarc.testing.TestGoals.km;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.ProgressionPlan;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ProgressionEngineTest {

    private static ProgressionPlan plan(double start, double fin, double hop) {
        return new ProgressionPlan(km(start), km(fin), km(hop));
    }

    @Test
    public void stepsFromOneToTenByOne() {
        List<Amount> steps = ProgressionEngine.steps(plan(1, 10, 1));
        assertEquals(10, steps.size());
        assertEquals(km(1), steps.get(0));
        assertEquals(km(10), steps.get(9));
        assertEquals(10, ProgressionEngine.totalSteps(plan(1, 10, 1)));
    }

    @Test
    public void stepsFromTwoToTenByTwo() {
        assertEquals(Arrays.asList(km(2), km(4), km(6), km(8), km(10)), ProgressionEngine.steps(plan(2, 10, 2)));
    }

    @Test
    public void lastStepIsClampedToFinalTarget() {
        // 1, 3, 5, 7, 9 -> next would be 11, clamped to exactly 10.
        List<Amount> steps = ProgressionEngine.steps(plan(1, 10, 2));
        assertEquals(Arrays.asList(km(1), km(3), km(5), km(7), km(9), km(10)), steps);
        assertEquals(6, ProgressionEngine.totalSteps(plan(1, 10, 2)));
        assertEquals(km(10), ProgressionEngine.nextTarget(km(9), plan(1, 10, 2)));
    }

    @Test
    public void targetsNeverExceedFinal() {
        ProgressionPlan p = plan(1, 10, 3);
        Amount t = p.startTarget();
        for (int i = 0; i < 20; i++) {
            t = ProgressionEngine.nextTarget(t, p);
            assertFalse(t.greaterThan(p.finalTarget()));
        }
        assertEquals(km(10), t);
    }

    @Test
    public void decimalHopsAreExact() {
        ProgressionPlan p = plan(3, 10, 0.5);
        assertEquals(15, ProgressionEngine.totalSteps(p));
        Amount t = p.startTarget();
        for (int i = 0; i < 14; i++) t = ProgressionEngine.nextTarget(t, p);
        assertEquals(km(10), t);
        // 0.1 increments 14 times from 0.1 must land exactly on 1.5
        ProgressionPlan tenths = plan(0.1, 1.5, 0.1);
        Amount x = tenths.startTarget();
        for (int i = 0; i < 14; i++) x = ProgressionEngine.nextTarget(x, tenths);
        assertEquals(km(1.5), x);
    }

    @Test
    public void completingFinalTargetReachesMilestone() {
        ProgressionPlan p = plan(1, 10, 1);
        ProgressionEngine.CompletionOutcome mid = ProgressionEngine.onCompleted(km(9), p);
        assertFalse(mid.milestoneReached());
        assertEquals(km(10), mid.nextTarget());
        ProgressionEngine.CompletionOutcome last = ProgressionEngine.onCompleted(km(10), p);
        assertTrue(last.milestoneReached());
        assertEquals(km(10), last.nextTarget());
    }

    @Test
    public void levelAndFraction() {
        ProgressionPlan p = plan(1, 10, 1);
        assertEquals(1, ProgressionEngine.level(km(1), p));
        assertEquals(3, ProgressionEngine.level(km(3), p));
        assertEquals(10, ProgressionEngine.level(km(10), p));
        // Off-grid target after an edit rounds up to the step being worked toward.
        assertEquals(4, ProgressionEngine.level(km(3.5), p));
        assertEquals(0f, ProgressionEngine.completedFraction(km(1), p, false), 0.0001f);
        assertEquals(0.9f, ProgressionEngine.completedFraction(km(10), p, false), 0.0001f);
        assertEquals(1f, ProgressionEngine.completedFraction(km(10), p, true), 0.0001f);
        assertEquals(1, ProgressionEngine.remainingCompletions(km(10), p, false));
        assertEquals(0, ProgressionEngine.remainingCompletions(km(10), p, true));
    }

    @Test
    public void rebasePreservesProgressWithinNewPlan() {
        assertEquals(km(5), ProgressionEngine.rebase(km(5), plan(1, 15, 2)));
        assertEquals(km(8), ProgressionEngine.rebase(km(9), plan(1, 8, 1)));
        assertEquals(km(3), ProgressionEngine.rebase(km(2), plan(3, 8, 1)));
    }

    @Test
    public void continueBeyondStartsOneHopAboveOldFinal() {
        ProgressionEngine.ContinuedPlan c = ProgressionEngine.continueBeyond(km(10), km(15), km(2));
        assertEquals(km(12), c.nextTarget());
        assertEquals(km(10), c.plan().startTarget());
        ProgressionEngine.ContinuedPlan clamp = ProgressionEngine.continueBeyond(km(10), km(11), km(2));
        assertEquals(km(11), clamp.nextTarget());
    }

    @Test(expected = IllegalArgumentException.class)
    public void continueBeyondRejectsLowerFinal() {
        ProgressionEngine.continueBeyond(km(10), km(10), km(1));
    }

    @Test
    public void invalidPlansAreRejected() {
        assertFalse(ProgressionEngine.isValid(plan(5, 5, 1)));
        assertFalse(ProgressionEngine.isValid(new ProgressionPlan(km(1), km(5), Amount.ZERO)));
        assertFalse(ProgressionEngine.isValid(new ProgressionPlan(Amount.ZERO, km(5), km(1))));
    }
}
