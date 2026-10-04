package com.app.winterarc.domain.model;

import androidx.annotation.Nullable;

/**
 * Curated, attributable content. Facts and quotes must carry a source name and URL; anything that
 * cannot be sourced is limited to {@link ContentType#ENCOURAGEMENT} messages.
 *
 * @param activityType null means the content applies to every activity
 * @param minTargetKm  inclusive lower bound in kilometres, null for no bound
 * @param maxTargetKm  inclusive upper bound in kilometres, null for no bound
 */
public record MotivationalContent(
        String id,
        ContentType contentType,
        @Nullable ActivityType activityType,
        @Nullable Amount minTargetKm,
        @Nullable Amount maxTargetKm,
        String message,
        @Nullable String attribution,
        @Nullable String sourceName,
        @Nullable String sourceUrl,
        String locale) {

    public boolean isAttributed() {
        return sourceName != null && !sourceName.isBlank() && sourceUrl != null && !sourceUrl.isBlank();
    }

    /** Facts and quotes are only shown when they carry a verifiable source. */
    public boolean isShowable() {
        if (contentType == ContentType.FACT || contentType == ContentType.QUOTE) return isAttributed();
        return true;
    }

    public boolean matches(ActivityType activity, @Nullable Amount targetKm) {
        if (activityType != null && activityType != activity) return false;
        if (targetKm == null) return minTargetKm == null && maxTargetKm == null;
        if (minTargetKm != null && targetKm.lessThan(minTargetKm)) return false;
        return maxTargetKm == null || !targetKm.greaterThan(maxTargetKm);
    }
}
