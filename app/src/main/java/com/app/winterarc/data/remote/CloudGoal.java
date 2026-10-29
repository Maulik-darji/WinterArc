package com.app.winterarc.data.remote;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** JSON representation of public.goals. Local Room ids deliberately never leave the device. */
public final class CloudGoal {
    public String uuid;

    @SerializedName("user_id") public String userId;
    public String title;
    @Nullable public String description;
    @SerializedName("activity_type") public String activityType;
    @SerializedName("tracking_mode") public String trackingMode;
    public String unit;
    @Nullable @SerializedName("custom_unit_label") public String customUnitLabel;
    @SerializedName("current_target_milli") public long currentTargetMilli;
    @Nullable @SerializedName("start_target_milli") public Long startTargetMilli;
    @Nullable @SerializedName("final_target_milli") public Long finalTargetMilli;
    @Nullable @SerializedName("hop_milli") public Long hopMilli;
    @SerializedName("schedule_mask") public int scheduleMask;
    @SerializedName("start_date") public String startDate;
    @Nullable @SerializedName("end_date") public String endDate;
    @SerializedName("reminder_enabled") public boolean reminderEnabled;
    @SerializedName("reminder_minute_of_day") public int reminderMinuteOfDay;
    public String color;
    public String icon;
    public String status;
    @SerializedName("created_at") public String createdAt;
    @SerializedName("updated_at") public String updatedAt;
}
