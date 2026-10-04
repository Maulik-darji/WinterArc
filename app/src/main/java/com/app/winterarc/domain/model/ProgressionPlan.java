package com.app.winterarc.domain.model;

/**
 * Progression plan; absent for consistency goals.
 * Invariant (checked by ProgressionEngine): {@code 0 < start < final} and {@code hop > 0}.
 */
public record ProgressionPlan(Amount startTarget, Amount finalTarget, Amount hop) {}
