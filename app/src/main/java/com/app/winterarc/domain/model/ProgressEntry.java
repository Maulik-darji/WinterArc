package com.app.winterarc.domain.model;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One logged day for a goal.
 *
 * @param scheduledTarget     the target that applied on this day; preserved if the plan is edited
 * @param progressionLevel    1-based progression step at the time, null for consistency goals
 * @param advancedProgression true when this completion advanced the progression target, so that
 *                            undoing it can revert exactly that advancement
 */
public record ProgressEntry(
        long id,
        String uuid,
        long goalId,
        LocalDate date,
        Amount scheduledTarget,
        Amount actualValue,
        EntryStatus status,
        @Nullable SkipReason skipReason,
        @Nullable String note,
        @Nullable Integer progressionLevel,
        boolean advancedProgression,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isCompleted() {
        return status == EntryStatus.COMPLETED;
    }

    public boolean isExceeded() {
        return isCompleted() && actualValue.greaterThan(scheduledTarget);
    }

    public ProgressEntry withId(long value) {
        return new ProgressEntry(value, uuid, goalId, date, scheduledTarget, actualValue, status, skipReason,
                note, progressionLevel, advancedProgression, createdAt, updatedAt);
    }

    public ProgressEntry withNote(@Nullable String value, Instant now) {
        return new ProgressEntry(id, uuid, goalId, date, scheduledTarget, actualValue, status, skipReason,
                value, progressionLevel, advancedProgression, createdAt, now);
    }

    /** Returns a copy with new progress values, keeping identity and creation time. */
    public ProgressEntry withProgress(Amount target, Amount actual, EntryStatus newStatus,
                                      @Nullable SkipReason reason, @Nullable String newNote,
                                      @Nullable Integer level, boolean advanced, Instant now) {
        return new ProgressEntry(id, uuid, goalId, date, target, actual, newStatus, reason, newNote, level,
                advanced, createdAt, now);
    }
}
