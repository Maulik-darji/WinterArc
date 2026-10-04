package com.app.winterarc.reminders;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.app.winterarc.MainActivity;
import com.app.winterarc.R;

public final class Notifications {
    public static final String CHANNEL_REMINDERS = "daily_reminders";
    public static final String EXTRA_GOAL_ID = "com.app.winterarc.extra.GOAL_ID";

    private Notifications() {}

    public static void createChannels(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.notification_channel_reminders), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_reminders_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    @SuppressWarnings("MissingPermission") // checked by canPost()
    public static void showReminder(Context context, long goalId, String title, String text) {
        if (!canPost(context)) return;
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_GOAL_ID, goalId);
        PendingIntent pending = PendingIntent.getActivity(context, (int) goalId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER);
        NotificationManagerCompat.from(context).notify((int) goalId, builder.build());
    }
}
