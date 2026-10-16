package com.humanappeal.mediaplan;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class HumanAppealFirebaseMessagingService extends FirebaseMessagingService {
    private static final int USA_NOTIFICATION_ID = 2201;
    private static final int AR_NOTIFICATION_ID = 2202;

    private static final Object SOUND_LOCK = new Object();
    private static AudioTrack activeTrack;
    private static Ringtone activeRingtone;

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        HumanAppealApp.applyTopicSubscription(this);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);

        if (!HumanAppealApp.notificationsEnabled(this)) return;

        String title = "Media Plan";
        String body = "Media Plan data was updated.";
        String accountKey = "usa";

        Map<String, String> data = message.getData();
        if (data != null) {
            String dataTitle = data.get("title");
            String dataBody = data.get("body");
            String dataAccount = data.get("accountKey");
            if (dataTitle != null && !dataTitle.isEmpty()) title = dataTitle;
            if (dataBody != null && !dataBody.isEmpty()) body = dataBody;
            if ("ar".equalsIgnoreCase(dataAccount)) accountKey = "ar";
        }

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null && !message.getNotification().getTitle().isEmpty()) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null && !message.getNotification().getBody().isEmpty()) {
                body = message.getNotification().getBody();
            }
        }

        showNotification(title, body, accountKey);
    }

    private void showNotification(String title, String body, String accountKey) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            MainActivity.createNotificationChannel(this);

            Intent open = new Intent(this, MainActivity.class);
            open.putExtra(MainActivity.EXTRA_OPEN_ACCOUNT, accountKey);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    "ar".equals(accountKey) ? AR_NOTIFICATION_ID : USA_NOTIFICATION_ID,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(this, MainActivity.CHANNEL_ID)
                    : new Notification.Builder(this);

            builder.setSmallIcon(R.drawable.ic_stat_humanappeal)
                    .setColor(android.graphics.Color.rgb(111, 45, 189))
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(false);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setSound(null);
            }

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                int id = "ar".equals(accountKey) ? AR_NOTIFICATION_ID : USA_NOTIFICATION_ID;
                manager.notify(id, builder.build());
                playSelectedSound(this, HumanAppealApp.notificationSound(this));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void playSelectedSound(Context context, String sound) {
        if (context == null) return;

        final Context appContext = context.getApplicationContext();
        final String safe = HumanAppealApp.sanitizeSound(sound);
        stopActiveSound();

        if ("default".equals(safe)) {
            playDefaultNotificationSound(appContext);
            return;
        }

        new Thread(() -> {
            AudioTrack track = null;
            try {
                final int sampleRate = 24000;
                short[] samples = synthesizeTone(safe, sampleRate);
                if (samples.length == 0) return;

                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(samples.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                track.write(samples, 0, samples.length);
                synchronized (SOUND_LOCK) {
                    activeTrack = track;
                }
                track.play();
                long durationMs = Math.max(120, (samples.length * 1000L) / sampleRate);
                Thread.sleep(durationMs + 80);
            } catch (Throwable ignored) {
            } finally {
                synchronized (SOUND_LOCK) {
                    if (activeTrack == track) activeTrack = null;
                }
                if (track != null) {
                    try { track.stop(); } catch (Throwable ignored) {}
                    try { track.release(); } catch (Throwable ignored) {}
                }
            }
        }, "ha-notification-tone").start();
    }

    private static void stopActiveSound() {
        synchronized (SOUND_LOCK) {
            if (activeRingtone != null) {
                try { activeRingtone.stop(); } catch (Throwable ignored) {}
                activeRingtone = null;
            }
            if (activeTrack != null) {
                try { activeTrack.pause(); } catch (Throwable ignored) {}
                try { activeTrack.flush(); } catch (Throwable ignored) {}
                try { activeTrack.stop(); } catch (Throwable ignored) {}
                try { activeTrack.release(); } catch (Throwable ignored) {}
                activeTrack = null;
            }
        }
    }

    private static void playDefaultNotificationSound(Context context) {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
            if (ringtone != null) {
                synchronized (SOUND_LOCK) {
                    activeRingtone = ringtone;
                }
                ringtone.play();
            }
        } catch (Throwable ignored) {
        }
    }

    private static short[] synthesizeTone(String style, int sampleRate) {
        final double duration;
        if ("gentle_bell".equals(style)) duration = 0.58;
        else if ("soft_ping".equals(style)) duration = 0.24;
        else duration = 0.48;

        int count = Math.max(1, (int) (sampleRate * duration));
        short[] out = new short[count];

        for (int i = 0; i < count; i++) {
            double t = i / (double) sampleRate;
            double sample;
            double envelope;

            if ("gentle_bell".equals(style)) {
                envelope = Math.exp(-5.0 * t);
                sample = Math.sin(2.0 * Math.PI * 784.0 * t) * 0.72
                        + Math.sin(2.0 * Math.PI * 1568.0 * t) * 0.20
                        + Math.sin(2.0 * Math.PI * 2352.0 * t) * 0.08;
            } else if ("soft_ping".equals(style)) {
                envelope = Math.exp(-11.0 * t);
                sample = Math.sin(2.0 * Math.PI * 1180.0 * t);
            } else {
                double split = 0.20;
                double freq = t < split ? 660.0 : 880.0;
                double localT = t < split ? t : t - split;
                double localDuration = t < split ? split : duration - split;
                double edge = Math.min(1.0, Math.min(localT / 0.025, (localDuration - localT) / 0.045));
                envelope = Math.max(0.0, edge) * (t < split ? 0.72 : 0.88);
                sample = Math.sin(2.0 * Math.PI * freq * t);
            }

            double value = Math.max(-1.0, Math.min(1.0, sample * envelope * 0.55));
            out[i] = (short) (value * Short.MAX_VALUE);
        }
        return out;
    }
}
