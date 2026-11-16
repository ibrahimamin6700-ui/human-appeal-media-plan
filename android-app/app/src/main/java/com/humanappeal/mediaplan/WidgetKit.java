package com.humanappeal.mediaplan;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.util.Base64;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/** HEMERA home-screen widgets: the numbers come from the app (AndroidShare.onWidgetData) and are kept here. */
public final class WidgetKit {
    private WidgetKit() {}

    static final String PREFS = "hm_widgets";
    static final String KEY_DATA = "data";

    // ---------- data from the app ----------
    static void save(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            String avatar = o.optString("avatar", "");
            o.remove("avatar");
            if (avatar.startsWith("data:image/")) {
                int i = avatar.indexOf(",");
                byte[] b = Base64.decode(avatar.substring(i + 1), Base64.DEFAULT);
                writeFile(new File(c.getFilesDir(), "w_avatar.img"), b);
            }
            JSONObject season = o.optJSONObject("season");
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String oldImg = p.getString("season_img", "");
            if (season != null) {
                String img = season.optString("img", "");
                File f = new File(c.getFilesDir(), "w_season.img");
                if (!img.isEmpty() && (!img.equals(oldImg) || !f.exists())) {
                    byte[] b = download(img);
                    if (b != null) { writeFile(f, b); p.edit().putString("season_img", img).apply(); }
                }
            }
            p.edit().putString(KEY_DATA, o.toString()).apply();
        } catch (Throwable ignored) {}
        updateAll(c);
    }

    static JSONObject data(Context c) {
        try { return new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DATA, "{}")); }
        catch (Throwable e) { return new JSONObject(); }
    }

    // ---------- the clock: one update a minute ----------
    static final String TICK = "com.humanappeal.mediaplan.CLOCK_TICK";

    static void scheduleClock(Context c) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            long next = (System.currentTimeMillis() / 60000L + 1) * 60000L;
            am.setRepeating(android.app.AlarmManager.RTC, next, 60000L, tickIntent(c));
        } catch (Throwable ignored) {}
    }

    static void cancelClock(Context c) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(tickIntent(c));
        } catch (Throwable ignored) {}
    }

    static PendingIntent tickIntent(Context c) {
        Intent i = new Intent(c, WidgetClock.class);
        i.setAction(TICK);
        return PendingIntent.getBroadcast(c, 4200, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateClocks(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, WidgetClock.class));
            if (ids == null || ids.length == 0) { cancelClock(c); return; }
            update(c, m, ids, WidgetClock.class);
        } catch (Throwable ignored) {}
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        Class<?>[] all = {WidgetAlexandra.class, WidgetValue.class, WidgetStreak.class, WidgetSeason.class, WidgetClock.class};
        for (Class<?> k : all) {
            try {
                int[] ids = m.getAppWidgetIds(new ComponentName(c, k));
                if (ids != null && ids.length > 0) update(c, m, ids, k);
            } catch (Throwable ignored) {}
        }
    }

    static void update(Context c, AppWidgetManager m, int[] ids, Class<?> k) {
        for (int id : ids) {
            try {
                RemoteViews v;
                if (k == WidgetAlexandra.class) v = alexandra(c);
                else if (k == WidgetValue.class) v = value(c);
                else if (k == WidgetStreak.class) v = streak(c);
                else if (k == WidgetSeason.class) v = season(c);
                else v = clock(c);
                m.updateAppWidget(id, v);
            } catch (Throwable ignored) {}
        }
    }

    // ---------- taps: open a place in HEMERA ----------
    static PendingIntent go(Context c, String where, int code) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_OPEN_GO, where);
        i.setData(Uri.parse("hemera://go/" + code));
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 4100 + code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---------- the widgets ----------
    static RemoteViews alexandra(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_alexandra);
        v.setImageViewBitmap(R.id.w_avatar, avatar(c, 138));
        v.setOnClickPendingIntent(R.id.w_bar, go(c, "ak:chat", 1));
        v.setOnClickPendingIntent(R.id.w_mic, go(c, "ak:voice", 2));
        v.setOnClickPendingIntent(R.id.w_c1, go(c, "ak:nums", 3));
        v.setOnClickPendingIntent(R.id.w_c2, go(c, "ak:sheets", 4));
        v.setOnClickPendingIntent(R.id.w_c3, go(c, "ak:voice", 5));
        return v;
    }

    static RemoteViews value(Context c) {
        JSONObject d = data(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_value);
        String cur = d.optString("cur", "");
        double val = d.optDouble("value", 0), plan = d.optDouble("plan", 0);
        v.setTextViewText(R.id.w_val, val > 0 ? cur + whole(val) : "—");
        v.setTextViewText(R.id.w_plan, plan > 0 ? "latest day · plan " + cur + whole(plan) : "open HEMERA to load");
        if (d.has("wk") && !d.isNull("wk")) {
            int wk = d.optInt("wk");
            v.setTextViewText(R.id.w_wk, (wk >= 0 ? "+" : "") + wk + "% vs last week");
            v.setTextColor(R.id.w_wk, wk >= 0 ? 0xFF1E9E4A : 0xFFD13B42);
        } else v.setTextViewText(R.id.w_wk, "");
        JSONArray a = d.optJSONArray("v14");
        v.setImageViewBitmap(R.id.w_chart, spark(a, 640, 150));
        v.setOnClickPendingIntent(R.id.w_root, go(c, "tab:overview", 6));
        return v;
    }

    static RemoteViews streak(Context c) {
        JSONObject d = data(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_streak);
        int n = d.optInt("streak", -1);
        v.setTextViewText(R.id.w_days, n < 0 ? "— days" : n == 1 ? "1 day" : n + " days");
        JSONArray s5 = d.optJSONArray("s5");
        v.setImageViewBitmap(R.id.w_bars, bars(s5));
        v.setOnClickPendingIntent(R.id.w_root, go(c, "tab:overview", 7));
        return v;
    }

    static RemoteViews season(Context c) {
        JSONObject d = data(c), s = d.optJSONObject("season");
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_season);
        if (s != null) {
            v.setTextViewText(R.id.w_name, s.optString("name", ""));
            int left = s.optInt("days", 0);
            v.setTextViewText(R.id.w_left, String.valueOf(left));
            v.setTextViewText(R.id.w_when, (left == 1 ? "day · " : "days · ") + s.optString("from", ""));
            v.setOnClickPendingIntent(R.id.w_root, go(c, "ss:" + s.optString("key", ""), 8));
        } else {
            v.setTextViewText(R.id.w_name, "Open HEMERA");
            v.setOnClickPendingIntent(R.id.w_root, go(c, "tab:seasons", 8));
        }
        v.setImageViewBitmap(R.id.w_img, seasonImage(c, 420, 420));
        return v;
    }

    static RemoteViews clock(Context c) {
        JSONObject d = data(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
        v.setImageViewBitmap(R.id.w_sky, sky(c, 620, 330));
        // the time in English (the phone may be in Arabic), refreshed every minute
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Muscat"));
        java.text.SimpleDateFormat t = new java.text.SimpleDateFormat("h:mm", Locale.US), ap = new java.text.SimpleDateFormat("a", Locale.US), dt = new java.text.SimpleDateFormat("EEE d MMM", Locale.US);
        TimeZone tz = TimeZone.getTimeZone("Asia/Muscat");
        t.setTimeZone(tz); ap.setTimeZone(tz); dt.setTimeZone(tz);
        v.setTextViewText(R.id.w_time, t.format(now.getTime()));
        v.setTextViewText(R.id.w_ampm, ap.format(now.getTime()));
        v.setTextViewText(R.id.w_date, "Muscat · " + dt.format(now.getTime()));
        double r = d.optDouble("roas", 0);
        String acc = "ar".equals(d.optString("account")) ? "AR" : "USA";
        v.setTextViewText(R.id.w_roas, r > 0 ? acc + " · " + String.format(Locale.US, "%.2fx", r) : "HEMERA");
        v.setOnClickPendingIntent(R.id.w_root, go(c, "tab:overview", 9));
        return v;
    }

    // ---------- drawing ----------
    static String whole(double n) { return NumberFormat.getIntegerInstance(Locale.US).format(Math.round(n)); }

    static float dp(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }

    /** Alexandra's photo in a circle with a gold ring and a green dot. */
    static Bitmap avatar(Context c, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = size / 2f, ring = size * 0.045f;
        p.setColor(0xFFE2B874);
        cv.drawCircle(r, r, r, p);
        Bitmap src = null;
        try {
            File f = new File(c.getFilesDir(), "w_avatar.img");
            if (f.exists()) src = BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable ignored) {}
        float ir = r - ring;
        if (src != null) {
            Bitmap circ = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cc = new Canvas(circ);
            Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
            cc.drawCircle(r, r, ir, m);
            m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            int s = Math.min(src.getWidth(), src.getHeight());
            android.graphics.Rect from = new android.graphics.Rect((src.getWidth() - s) / 2, (src.getHeight() - s) / 2, (src.getWidth() + s) / 2, (src.getHeight() + s) / 2);
            cc.drawBitmap(src, from, new RectF(0, 0, size, size), m);
            cv.drawBitmap(circ, 0, 0, null);
        } else {
            p.setShader(new RadialGradient(r * 0.7f, r * 0.6f, ir, 0xFF3557C4, 0xFF132A73, Shader.TileMode.CLAMP));
            cv.drawCircle(r, r, ir, p);
            p.setShader(null);
            p.setColor(0xFFF3D58E);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(size * 0.42f);
            p.setFakeBoldText(true);
            cv.drawText("A", r, r + size * 0.15f, p);
        }
        float dr = size * 0.13f, dx = size * 0.84f, dy = size * 0.84f;
        p.setColor(Color.WHITE);
        cv.drawCircle(dx, dy, dr + size * 0.035f, p);
        p.setColor(0xFF31A24C);
        cv.drawCircle(dx, dy, dr, p);
        return out;
    }

    static Bitmap spark(JSONArray a, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        if (a == null || a.length() < 2) return b;
        Canvas cv = new Canvas(b);
        double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
        for (int i = 0; i < a.length(); i++) { double x = a.optDouble(i, 0); mn = Math.min(mn, x); mx = Math.max(mx, x); }
        if (mx - mn < 1) mx = mn + 1;
        float pad = 10, step = (w - 2 * pad) / (a.length() - 1);
        Path line = new Path(), fill = new Path();
        float lx = 0, ly = 0;
        for (int i = 0; i < a.length(); i++) {
            float x = pad + i * step, y = (float) (h - pad - (a.optDouble(i, 0) - mn) / (mx - mn) * (h - 2.6 * pad));
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, h); fill.lineTo(x, y); } else { line.lineTo(x, y); fill.lineTo(x, y); }
            lx = x; ly = y;
        }
        fill.lineTo(lx, h); fill.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x241E9E4A);
        cv.drawPath(fill, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF1E9E4A);
        cv.drawPath(line, p);
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(lx, ly, 8f, p);
        return b;
    }

    static Bitmap bars(JSONArray s5) {
        int w = 330, h = 24;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 5; i++) {
            boolean on = s5 != null && i < s5.length() && s5.optInt(i) == 1;
            p.setColor(on ? 0xFF1E9E4A : 0xFFE8D3A6);
            float x = i * 66;
            cv.drawRoundRect(new RectF(x, 0, x + 54, h), h / 2f, h / 2f, p);
        }
        return b;
    }

    static Bitmap seasonImage(Context c, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path clip = new Path();
        float rad = w * 0.11f;
        clip.addRoundRect(new RectF(0, 0, w, h), rad, rad, Path.Direction.CW);
        cv.clipPath(clip);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF2A4BB3, 0xFF0F2366, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        try {
            File f = new File(c.getFilesDir(), "w_season.img");
            if (f.exists()) {
                Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (src != null) {
                    float sc = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
                    float dw = src.getWidth() * sc, dh = src.getHeight() * sc;
                    cv.drawBitmap(src, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), new Paint(Paint.FILTER_BITMAP_FLAG));
                }
            }
        } catch (Throwable ignored) {}
        p.setShader(new LinearGradient(0, h * 0.25f, 0, h, 0x330A1238, 0xF20A1238, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        return out;
    }

    /** The Muscat sky for the clock: night, dawn, day or dusk, with the moon or the sun and the city skyline. */
    static Bitmap sky(Context c, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Muscat"));
        float hr = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f;
        boolean day = hr >= 6 && hr < 18;
        boolean edge = (hr >= 5 && hr < 7) || (hr >= 17 && hr < 19);
        int top, bottom;
        if (edge) { top = 0xFFFFD27E; bottom = 0xFFD0607E; }
        else if (day) { top = 0xFF7CC4FF; bottom = 0xFF2B5FC4; }
        else { top = 0xFF24357F; bottom = 0xFF0B1236; }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path clip = new Path();
        float rad = h * 0.15f;
        clip.addRoundRect(new RectF(0, 0, w, h), rad, rad, Path.Direction.CW);
        cv.clipPath(clip);
        p.setShader(new LinearGradient(0, 0, w * 0.3f, h, top, bottom, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        if (!day) {
            Random rnd = new Random(7);
            p.setColor(0xDDFFFFFF);
            for (int i = 0; i < 26; i++) cv.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.7f, 1.2f + rnd.nextFloat() * 1.6f, p);
        }
        // the moon or the sun moves along the sky with the hour
        float f = day ? (hr - 6) / 12f : ((hr >= 18 ? hr - 18 : hr + 6) / 12f);
        float ox = w * (0.55f + 0.35f * f), oy = h * (0.55f - 0.32f * (float) Math.sin(f * Math.PI));
        float or = h * 0.1f;
        if (day && !edge) {
            p.setShader(new RadialGradient(ox, oy, or * 2.4f, 0x88FFF1A6, 0x00FFF1A6, Shader.TileMode.CLAMP));
            cv.drawCircle(ox, oy, or * 2.4f, p);
            p.setShader(new RadialGradient(ox, oy, or, 0xFFFFFBE0, 0xFFFFD65E, Shader.TileMode.CLAMP));
            cv.drawCircle(ox, oy, or, p);
            p.setShader(null);
        } else if (edge) {
            p.setColor(0xFFFFF3C4);
            cv.drawCircle(ox, oy, or, p);
        } else {
            p.setShader(new RadialGradient(ox, oy, or * 2.2f, 0x55E8ECFF, 0x00E8ECFF, Shader.TileMode.CLAMP));
            cv.drawCircle(ox, oy, or * 2.2f, p);
            p.setShader(null);
            p.setColor(0xFFEEF1FF);
            cv.drawCircle(ox, oy, or, p);
            p.setColor(top);
            cv.drawCircle(ox + or * 0.45f, oy - or * 0.15f, or * 0.85f, p);
        }
        // Muscat skyline: low houses, a dome and minarets
        float sx = w / 262f, sy = h / 174f, base = h;
        Path s = new Path();
        float[][] pts = {{0, 44}, {18, 44}, {18, 36}, {28, 36}, {28, 44}, {40, 44}, {40, 30}, {46, 30}, {49, 22}, {52, 30}, {58, 30}, {58, 44}, {72, 44}, {72, 38}, {92, 38}, {92, 26}};
        s.moveTo(0, base);
        for (float[] q : pts) s.lineTo(q[0] * sx, base - (60 - q[1]) * sy);
        RectF dome = new RectF(92 * sx, base - (60 - 8) * sy, 128 * sx, base - (60 - 44) * sy);
        s.arcTo(dome, 180, 180);
        float[][] pts2 = {{128, 44}, {138, 44}, {138, 12}, {140, 6}, {142, 12}, {142, 44}, {154, 44}, {154, 34}, {172, 34}, {172, 44}, {186, 44}, {186, 36}, {196, 36}, {200, 26}, {204, 36}, {214, 36}, {214, 44}, {262, 44}};
        for (float[] q : pts2) s.lineTo(q[0] * sx, base - (60 - q[1]) * sy);
        s.lineTo(w, base);
        s.close();
        p.setColor(day && !edge ? 0x8C14286E : 0xD904081E);
        cv.drawPath(s, p);
        return out;
    }

    // ---------- files ----------
    static void writeFile(File f, byte[] b) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(b); }
    }

    static byte[] download(String u) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(u).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(10000);
            if (con.getResponseCode() != 200) return null;
            try (InputStream in = con.getInputStream()) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); if (bo.size() > 4_000_000) return null; }
                // keep the picture small for the widget
                byte[] all = bo.toByteArray();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                Bitmap bm = BitmapFactory.decodeByteArray(all, 0, all.length, o);
                if (bm == null) return null;
                java.io.ByteArrayOutputStream jo = new java.io.ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 85, jo);
                return jo.toByteArray();
            }
        } catch (Throwable e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
