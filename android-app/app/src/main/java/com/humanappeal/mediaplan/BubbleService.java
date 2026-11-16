package com.humanappeal.mediaplan;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

/** Alexandra's bubble over other apps (like a chat head). Tap opens her chat; drag it to the red X to close it. */
public class BubbleService extends Service {
    static final String CHANNEL = "alexandra_bubble";
    static final int NOTE_ID = 2301;
    static boolean running = false;

    private WindowManager wm;
    private ImageView bubble, target;
    private WindowManager.LayoutParams bp, tp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int size, tsize;
    private boolean hot = false;

    public static void start(Context c) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) return;
        Intent i = new Intent(c, BubbleService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable ignored) {}
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, BubbleService.class)); } catch (Throwable ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startAsForeground();
        // a short wait: the app may only be showing a system dialog (it comes back at once)
        h.postDelayed(this::show, 700);
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Alexandra bubble", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Alexandra is on your screen")
                .setContentText("Tap her bubble to chat, or drag it to the X to close it.")
                .setContentIntent(openChat(1))
                .setOngoing(true);
        Notification n = b.build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTE_ID, n);
        } catch (Throwable e) {
            stopSelf();
        }
    }

    private PendingIntent openChat(int code) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_OPEN_GO, "ak:chat");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 4300 + code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private int type() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void show() {
        if (MainActivity.inFront || bubble != null) { if (MainActivity.inFront) stopSelf(); return; }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        size = Math.round(62 * dm.density);
        tsize = Math.round(66 * dm.density);

        target = new ImageView(this);
        target.setImageBitmap(closeTarget(tsize));
        target.setAlpha(0f);
        tp = new WindowManager.LayoutParams(tsize, tsize, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        tp.gravity = Gravity.TOP | Gravity.START;
        tp.x = (dm.widthPixels - tsize) / 2;
        tp.y = dm.heightPixels - tsize - Math.round(110 * dm.density);
        try { wm.addView(target, tp); } catch (Throwable e) { target = null; }

        bubble = new ImageView(this);
        bubble.setImageBitmap(WidgetKit.avatar(this, size));
        bubble.setElevation(10 * dm.density);
        bp = new WindowManager.LayoutParams(size, size, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bp.gravity = Gravity.TOP | Gravity.START;
        int side = getSharedPreferences(WidgetKit.PREFS, MODE_PRIVATE).getInt("bub_side", 1);
        bp.x = side == 0 ? Math.round(10 * dm.density) : dm.widthPixels - size - Math.round(10 * dm.density);
        bp.y = getSharedPreferences(WidgetKit.PREFS, MODE_PRIVATE).getInt("bub_y", Math.round(dm.heightPixels * 0.35f));
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean moved;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                DisplayMetrics d = getResources().getDisplayMetrics();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); ox = bp.x; oy = bp.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                        if (!moved && Math.hypot(dx, dy) < 10 * d.density) return true;
                        moved = true;
                        bp.x = Math.round(ox + dx);
                        bp.y = Math.round(oy + dy);
                        // the red X shows once the bubble is in the lower half
                        boolean low = bp.y + size / 2f > d.heightPixels * 0.5f;
                        if (target != null) target.animate().alpha(low ? 1f : 0f).setDuration(150).start();
                        float cx = tp.x + tsize / 2f, cy = tp.y + tsize / 2f;
                        boolean near = low && Math.hypot(bp.x + size / 2f - cx, bp.y + size / 2f - cy) < 90 * d.density;
                        if (near != hot) {
                            hot = near;
                            if (target != null) target.animate().scaleX(near ? 1.25f : 1f).scaleY(near ? 1.25f : 1f).setDuration(120).start();
                            if (near) buzz();
                        }
                        if (near) { bp.x = Math.round(cx - size / 2f); bp.y = Math.round(cy - size / 2f); }
                        try { wm.updateViewLayout(bubble, bp); } catch (Throwable ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (target != null) target.animate().alpha(0f).scaleX(1f).scaleY(1f).setDuration(150).start();
                        if (!moved) {
                            try { openChat(2).send(); } catch (Throwable ignored) {}
                            stopSelf();
                            return true;
                        }
                        if (hot) { hot = false; stopSelf(); return true; }
                        snap(d);
                        return true;
                }
                return false;
            }
        });
        try { wm.addView(bubble, bp); } catch (Throwable e) { bubble = null; stopSelf(); }
    }

    private void snap(DisplayMetrics d) {
        final boolean left = bp.x + size / 2f < d.widthPixels / 2f;
        final int from = bp.x, to = left ? Math.round(10 * d.density) : d.widthPixels - size - Math.round(10 * d.density);
        bp.y = Math.max(Math.round(40 * d.density), Math.min(d.heightPixels - size - Math.round(60 * d.density), bp.y));
        getSharedPreferences(WidgetKit.PREFS, MODE_PRIVATE).edit().putInt("bub_side", left ? 0 : 1).putInt("bub_y", bp.y).apply();
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(220);
        a.addUpdateListener(an -> {
            bp.x = (int) an.getAnimatedValue();
            try { if (bubble != null) wm.updateViewLayout(bubble, bp); } catch (Throwable ignored) {}
        });
        a.start();
    }

    private void buzz() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(15);
        } catch (Throwable ignored) {}
    }

    private static Bitmap closeTarget(int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFE5484D);
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(s * 0.075f);
        p.setStrokeCap(Paint.Cap.ROUND);
        float a = s * 0.33f, z = s * 0.67f;
        c.drawLine(a, a, z, z, p);
        c.drawLine(z, a, a, z, p);
        return b;
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacksAndMessages(null);
        try { if (bubble != null) wm.removeView(bubble); } catch (Throwable ignored) {}
        try { if (target != null) wm.removeView(target); } catch (Throwable ignored) {}
        bubble = null; target = null;
        super.onDestroy();
    }
}
