package com.app.winterarc.domain.repository;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.ContentType;
import com.app.winterarc.domain.model.MotivationalContent;

import java.util.List;
import java.util.Set;

public interface ContentRepository {
    /** Loads the bundled offline content set if this content version is not stored yet. */
    @WorkerThread
    void ensureSeeded();

    @WorkerThread
    List<MotivationalContent> getAll();

    /**
     * Picks one showable item matching the activity and target. Facts and quotes without a
     * verifiable source are never returned. {@code seed} keeps the choice stable per day.
     */
    @WorkerThread @Nullable
    MotivationalContent pick(ActivityType activity, @Nullable Amount targetKm, Set<ContentType> types, int seed);
}
