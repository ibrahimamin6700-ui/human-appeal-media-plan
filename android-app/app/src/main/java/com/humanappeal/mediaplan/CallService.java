package com.humanappeal.mediaplan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Keeps a voice call with Alexandra going (mic and sound) when the screen is locked or the app is in the background, like a WhatsApp call. */
public class CallService extends Service {
    static final String CHANNEL = "alexandra_call";
    static final int NOTE_ID = 2302;
    static boolean running = false;
    private PowerManager.WakeLock lock;

    public static void start(Context c) {
        Intent i = new Intent(c, CallService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable ignored) {}
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, CallService.class)); } catch (Throwable ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Call with Alexandra", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Intent back = new Intent(this, MainActivity.class);
        back.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pi = PendingIntent.getActivity(this, 2302, back, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_humanappeal)
                .setContentTitle("On a call with Alexandra")
                .setContentText("Tap to go back to the call")
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setUsesChronometer(true);
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTE_ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTE_ID, b.build());
        } catch (Throwable t) {
            stopSelf();
            return;
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hemera:alexandra-call");
            lock.acquire(60 * 60 * 1000L);
        } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }

    @Override
    public void onTaskRemoved(Intent rootIntent) { stopSelf(); }

    @Override
    public void onDestroy() {
        running = false;
        try { if (lock != null && lock.isHeld()) lock.release(); } catch (Throwable ignored) {}
        try { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
