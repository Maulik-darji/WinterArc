package com.app.winterarc.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.WeeklySchedule;
import com.app.winterarc.testing.TestGoals;

import org.junit.Test;

import java.time.LocalDate;

public class CloudGoalMapperTest {
    @Test
    public void progressionGoal_roundTripsWithoutLocalId() {
        Goal local = TestGoals.progression(Amount.whole(1), Amount.whole(10), Amount.whole(1),
                LocalDate.of(2026, 10, 4));

        CloudGoal cloud = CloudGoalMapper.fromDomain(local);
        Goal restored = CloudGoalMapper.toDomain(cloud, 42L);

        assertEquals(42L, restored.id());
        assertEquals(local.uuid(), restored.uuid());
        assertEquals(local.title(), restored.title());
        assertEquals(local.activityType(), restored.activityType());
        assertEquals(local.trackingMode(), restored.trackingMode());
        assertEquals(local.currentTarget(), restored.currentTarget());
        assertEquals(local.plan(), restored.plan());
        assertEquals(local.schedule(), restored.schedule());
        assertEquals(local.createdAt(), restored.createdAt());
        assertEquals(local.updatedAt(), restored.updatedAt());
        assertNull(cloud.userId);
    }

    @Test
    public void consistencyGoal_keepsNullableProgressionFieldsEmpty() {
        Goal local = TestGoals.consistency(Amount.whole(5), WeeklySchedule.EVERY_DAY,
                LocalDate.of(2026, 10, 4));

        CloudGoal cloud = CloudGoalMapper.fromDomain(local);

        assertNull(cloud.startTargetMilli);
        assertNull(cloud.finalTargetMilli);
        assertNull(cloud.hopMilli);
    }

    @Test
    public void config_normalizesUrlAndRejectsMissingValues() {
        SupabaseConfig valid = new SupabaseConfig("https://project.supabase.co", "key", true);
        SupabaseConfig missing = new SupabaseConfig("", "", true);

        assertTrue(valid.configured());
        assertEquals("https://project.supabase.co/", valid.retrofitBaseUrl());
        assertFalse(missing.configured());
        assertEquals("https://example.invalid/", missing.retrofitBaseUrl());
    }
}
