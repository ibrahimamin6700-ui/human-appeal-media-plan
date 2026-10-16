package com.humanappeal.mediaplan;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

/**
 * Notifications that come from SEKNA itself (team messages, new app versions, access approval)
 * use the SEKNA "S" mark. Media-plan data notifications keep the Human Appeal mark.
 */
public final class SeknaNotifications {
    public static final int ANNOUNCEMENT_ID = 2203;
    public static final int APP_UPDATE_ID = 2204;
    public static final int ACCESS_ID = 2205;
    public static final String EXTRA_CHECK_UPDATE = "check_app_update";

    private static final int SEKNA_NAVY = Color.rgb(32, 60, 142);

    private SeknaNotifications() {}

    public static void show(Context context, int id, String title, String body, boolean checkUpdate) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            MainActivity.createNotificationChannel(context);

            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (checkUpdate) open.putExtra(EXTRA_CHECK_UPDATE, true);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, MainActivity.CHANNEL_ID)
                    : new Notification.Builder(context);
            builder.setSmallIcon(R.drawable.ic_stat_sekna)
                    .setColor(SEKNA_NAVY)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) builder.setSound(null);

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(id, builder.build());
                HumanAppealFirebaseMessagingService.playSelectedSound(context, HumanAppealApp.notificationSound(context));
            }
        } catch (Throwable ignored) {
        }
    }
}
