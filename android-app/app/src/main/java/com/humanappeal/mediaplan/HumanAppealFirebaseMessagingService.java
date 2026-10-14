package com.humanappeal.mediaplan;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class HumanAppealFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "media_plan_updates";
    private static final int FCM_NOTIFICATION_ID = 2201;

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(HumanAppealApp.FCM_TOPIC);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);

        String title = "Media Plan USA";
        String body = "Media Plan data was updated.";

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null && !message.getNotification().getTitle().isEmpty()) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null && !message.getNotification().getBody().isEmpty()) {
                body = message.getNotification().getBody();
            }
        }

        Map<String, String> data = message.getData();
        if (data != null) {
            String dataTitle = data.get("title");
            String dataBody = data.get("body");
            if (dataTitle != null && !dataTitle.isEmpty()) title = dataTitle;
            if (dataBody != null && !dataBody.isEmpty()) body = dataBody;
        }

        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            MainActivity.createNotificationChannel(this);

            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    2201,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(this, CHANNEL_ID)
                    : new Notification.Builder(this);

            builder.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(FCM_NOTIFICATION_ID, builder.build());
        } catch (Throwable ignored) {
        }
    }
}
