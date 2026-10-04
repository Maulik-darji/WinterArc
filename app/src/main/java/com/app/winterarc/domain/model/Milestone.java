package com.app.winterarc.domain.model;

import java.time.Instant;
import java.time.LocalDate;

public record Milestone(
        long id,
        long goalId,
        Amount targetValue,
        LocalDate achievedDate,
        CelebrationState celebrationState,
        Instant createdAt) {

    public Milestone withState(CelebrationState state) {
        return new Milestone(id, goalId, targetValue, achievedDate, state, createdAt);
    }
}
