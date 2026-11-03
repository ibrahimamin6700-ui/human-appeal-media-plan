package com.humanappeal.mediaplan;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    public static final int TASKS_ID = 2206;
    public static final int GREETING_ID = 2207;
    public static final String EXTRA_OPEN_TASKS = "open_tasks";
    public static final String EXTRA_CHECK_UPDATE = "check_app_update";

    private static final int SEKNA_NAVY = Color.rgb(32, 60, 142);

    private SeknaNotifications() {}

    public static void show(Context context, int id, String title, String body, boolean checkUpdate) {
        show(context, id, title, body, checkUpdate, false);
    }

    public static void show(Context context, int id, String title, String body, boolean checkUpdate, boolean openTasks) {
        show(context, id, title, body, checkUpdate, openTasks, null);
    }

    /** A season greeting: the season's picture with the greeting under it (text only if the picture cannot load). */
    public static void showPicture(Context context, int id, String title, String body, String imageUrl) {
        show(context, id, title, body, false, false, loadPicture(imageUrl));
    }

    private static Bitmap loadPicture(String url) {
        if (url == null || !url.startsWith("https://")) return null;
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            try (java.io.InputStream in = c.getInputStream()) { return BitmapFactory.decodeStream(in); }
        } catch (Throwable e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void show(Context context, int id, String title, String body, boolean checkUpdate, boolean openTasks, Bitmap picture) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            MainActivity.createNotificationChannel(context);

            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (checkUpdate) open.putExtra(EXTRA_CHECK_UPDATE, true);
            if (openTasks) open.putExtra(EXTRA_OPEN_TASKS, true);
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
            if (picture != null) {
                builder.setLargeIcon(picture)
                        .setStyle(new Notification.BigPictureStyle().bigPicture(picture).bigLargeIcon((Bitmap) null).setSummaryText(body));
            }
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
