package com.app.winterarc.domain.engine;

import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.ProgressionPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure progression math. Targets only advance on a successful completion; calendar time alone
 * never changes them. Every computed target is clamped to the final milestone.
 */
public final class ProgressionEngine {

    /** Upper bound on generated steps so a pathological plan can't allocate unbounded lists. */
    public static final int MAX_STEPS = 1_000;

    private ProgressionEngine() {}

    public record CompletionOutcome(Amount nextTarget, boolean milestoneReached) {}

    public record ContinuedPlan(ProgressionPlan plan, Amount nextTarget) {}

    public static boolean isValid(ProgressionPlan plan) {
        return plan != null
                && plan.startTarget().isPositive()
                && plan.hop().isPositive()
                && plan.finalTarget().greaterThan(plan.startTarget());
    }

    /**
     * All targets of the plan, e.g. start 1, hop 1, final 10 gives [1, 2, ..., 10]. The last step
     * is always exactly the final target.
     */
    public static List<Amount> steps(ProgressionPlan plan) {
        requireValid(plan);
        List<Amount> result = new ArrayList<>();
        Amount target = plan.startTarget();
        while (target.lessThan(plan.finalTarget()) && result.size() < MAX_STEPS - 1) {
            result.add(target);
            target = nextTarget(target, plan);
        }
        result.add(plan.finalTarget());
        return result;
    }

    public static int totalSteps(ProgressionPlan plan) {
        requireValid(plan);
        long distance = plan.finalTarget().minus(plan.startTarget()).milli();
        long hops = ceilDiv(distance, plan.hop().milli());
        return (int) Math.min(hops + 1, MAX_STEPS);
    }

    /** The target after successfully completing {@code current}; never exceeds the final target. */
    public static Amount nextTarget(Amount current, ProgressionPlan plan) {
        requireValid(plan);
        if (current.atLeast(plan.finalTarget())) return plan.finalTarget();
        return Amount.min(current.plus(plan.hop()), plan.finalTarget());
    }

    /**
     * Outcome of completing {@code completedTarget}. Completing the final target reaches the
     * milestone; the target then stays at the final value.
     */
    public static CompletionOutcome onCompleted(Amount completedTarget, ProgressionPlan plan) {
        if (completedTarget.atLeast(plan.finalTarget())) {
            return new CompletionOutcome(plan.finalTarget(), true);
        }
        return new CompletionOutcome(nextTarget(completedTarget, plan), false);
    }

    /**
     * 1-based step index of {@code target}. Targets off the step grid (for example after a plan
     * edit) round up to the step they are working toward.
     */
    public static int level(Amount target, ProgressionPlan plan) {
        int total = totalSteps(plan);
        if (target.atLeast(plan.finalTarget())) return total;
        if (!target.greaterThan(plan.startTarget())) return 1;
        long distance = target.minus(plan.startTarget()).milli();
        long hops = ceilDiv(distance, plan.hop().milli());
        return (int) Math.max(1, Math.min(hops + 1, total));
    }

    /**
     * Fraction (0..1) of the plan completed. Working on step k of n means k-1 steps are done;
     * reaching the milestone means all n are done.
     */
    public static float completedFraction(Amount current, ProgressionPlan plan, boolean milestoneReached) {
        if (milestoneReached) return 1f;
        int total = totalSteps(plan);
        int done = level(current, plan) - 1;
        return Math.max(0f, Math.min(1f, done / (float) total));
    }

    /** Successful completions still needed to reach the milestone, including the current one. */
    public static int remainingCompletions(Amount current, ProgressionPlan plan, boolean milestoneReached) {
        if (milestoneReached) return 0;
        return totalSteps(plan) - level(current, plan) + 1;
    }

    /** Applies a plan edit while preserving progress: the current target is clamped into range. */
    public static Amount rebase(Amount current, ProgressionPlan newPlan) {
        requireValid(newPlan);
        if (current.lessThan(newPlan.startTarget())) return newPlan.startTarget();
        if (current.greaterThan(newPlan.finalTarget())) return newPlan.finalTarget();
        return current;
    }

    /**
     * Continues a finished plan toward a higher final target. The previous milestone was
     * completed, so the next attempt is one hop above it (clamped).
     */
    public static ContinuedPlan continueBeyond(Amount previousFinal, Amount newFinal, Amount hop) {
        if (!newFinal.greaterThan(previousFinal)) {
            throw new IllegalArgumentException("New final target must exceed the previous one");
        }
        if (!hop.isPositive()) throw new IllegalArgumentException("Hop must be positive");
        ProgressionPlan plan = new ProgressionPlan(previousFinal, newFinal, hop);
        return new ContinuedPlan(plan, nextTarget(previousFinal, plan));
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    private static void requireValid(ProgressionPlan plan) {
        if (!isValid(plan)) throw new IllegalArgumentException("Invalid progression plan: " + plan);
    }
}
