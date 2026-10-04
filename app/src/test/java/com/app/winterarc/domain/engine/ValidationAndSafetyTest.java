package com.app.winterarc.domain.engine;

import static com.app.winterarc.testing.TestGoals.km;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.GoalDraft;
import com.app.winterarc.domain.model.GoalUnit;
import com.app.winterarc.domain.model.TrackingMode;
import com.app.winterarc.domain.model.WeeklySchedule;

import org.junit.Test;

import java.time.LocalDate;

public class ValidationAndSafetyTest {

    private static GoalDraft running() {
        GoalDraft d = GoalDraft.forActivity(ActivityType.RUNNING, LocalDate.of(2026, 10, 2));
        d.title = "Road to 10K";
        d.trackingMode = TrackingMode.PROGRESSION;
        d.schedule = WeeklySchedule.of(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY);
        return d;
    }

    @Test
    public void validDraftHasNoErrors() {
        assertTrue(GoalValidator.validate(running()).isEmpty());
    }

    @Test
    public void reportsInputErrors() {
        GoalDraft d = running();
        d.title = "  ";
        d.finalTarget = d.startTarget;
        d.hop = Amount.ZERO;
        d.schedule = new WeeklySchedule(0);
        assertTrue(GoalValidator.validate(d).containsAll(java.util.List.of(
                GoalValidator.Error.TITLE_BLANK, GoalValidator.Error.FINAL_NOT_GREATER_THAN_START,
                GoalValidator.Error.HOP_NOT_POSITIVE, GoalValidator.Error.NO_ACTIVE_DAYS)));
    }

    @Test
    public void decimalsOnlyForDecimalUnits() {
        GoalDraft d = GoalDraft.forActivity(ActivityType.READING, LocalDate.of(2026, 10, 2));
        d.title = "Read";
        d.unit = GoalUnit.PAGES;
        d.consistencyTarget = Amount.parse("20.5");
        assertTrue(GoalValidator.validate(d).contains(GoalValidator.Error.DECIMALS_NOT_SUPPORTED));
    }

    @Test
    public void gentlePlanHasNoSafetyNotice() {
        assertEquals(SafetyAdvisor.Level.NONE, SafetyAdvisor.assess(running()).level());
    }

    @Test
    public void longDistanceShowsNotice() {
        GoalDraft d = running();
        d.finalTarget = km(21.1);
        SafetyAdvisor.Assessment a = SafetyAdvisor.assess(d);
        assertEquals(SafetyAdvisor.Level.NOTICE, a.level());
        assertTrue(a.reasons().contains(SafetyAdvisor.Reason.LONG_DISTANCE));
    }

    @Test
    public void aggressiveHopRequiresConfirmation() {
        GoalDraft d = running();
        d.hop = km(6);
        d.finalTarget = km(20);
        assertEquals(SafetyAdvisor.Level.CONFIRM, SafetyAdvisor.assess(d).level());
    }

    @Test
    public void dailyRunningProgressionSuggestsRestDays() {
        GoalDraft d = running();
        d.schedule = WeeklySchedule.EVERY_DAY;
        assertTrue(SafetyAdvisor.assess(d).reasons().contains(SafetyAdvisor.Reason.NO_REST_DAYS));
    }

    @Test
    public void nonEnduranceGoalsAreNeverFlagged() {
        GoalDraft d = GoalDraft.forActivity(ActivityType.READING, LocalDate.of(2026, 10, 2));
        d.consistencyTarget = Amount.whole(500);
        assertEquals(SafetyAdvisor.Level.NONE, SafetyAdvisor.assess(d).level());
    }
}
