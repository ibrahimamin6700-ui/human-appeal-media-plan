package com.humanappeal.mediaplan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String DASHBOARD_URL = "https://script.google.com/macros/s/AKfycbzjgZ1nXxLTcFdeFCIEznOj2H0XCo9YGUeu1YF2pJdsQiOo_aM-Mn6HxKKNk8peEuM1Ig/exec";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/ibrahimamin6700-ui/human-appeal-media-plan/releases/latest";
    private static final String APK_ASSET_NAME = "Human-Appeal-Media-Plan.apk";
    private static final String CHANNEL_ID = "media_plan_updates";
    private static final String PREFS = "media_plan_prefs";
    private static final String KEY_LAST_DAY = "last_dashboard_day";
    private static final int NOTIFICATION_ID = 1201;
    private static final int SYNC_JOB_ID = 12026;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private ProgressBar loadProgress;
    private View splashView;
    private boolean dashboardReady = false;
    private boolean servicesInitialized = false;

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        try {
            webView = new WebView(this);
        } catch (Throwable error) {
            showFallbackScreen(root);
            setContentView(root);
            return;
        }

        webView.setVisibility(View.VISIBLE);
        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        splashView = buildSplashView();
        root.addView(splashView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        safeEnableImmersiveMode();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidShare");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (loadProgress != null) {
                    int shown = Math.max(8, Math.min(progress, 96));
                    loadProgress.setProgress(shown);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                hideGoogleHostUi();
                mainHandler.postDelayed(MainActivity.this::hideGoogleHostUi, 250);
                mainHandler.postDelayed(MainActivity.this::hideGoogleHostUi, 900);
                mainHandler.postDelayed(() -> {
                    if (!dashboardReady) hideSplash();
                }, 3500);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost();
                if (host.endsWith("google.com") || host.endsWith("googleusercontent.com")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Unable to open this link", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(DASHBOARD_URL);
        } else {
            try {
                webView.restoreState(savedInstanceState);
            } catch (Throwable ignored) {
                webView.loadUrl(DASHBOARD_URL);
            }
        }

        mainHandler.postDelayed(this::checkForGitHubUpdate, 6500);
    }

    private View buildSplashView() {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setPadding(dp(34), dp(34), dp(34), dp(34));
        splash.setBackgroundColor(Color.WHITE);

        ImageView logo = new ImageView(this);
        Bitmap officialLogo = loadOfficialLogoBitmap();
        if (officialLogo != null) logo.setImageBitmap(officialLogo);
        else logo.setImageResource(R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(138), dp(138));
        logoParams.bottomMargin = dp(20);
        splash.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("Human Appeal USA");
        title.setTextColor(Color.rgb(34, 40, 50));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        splash.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Media Plan");
        subtitle.setTextColor(Color.rgb(126, 133, 145));
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(5);
        subParams.bottomMargin = dp(22);
        splash.addView(subtitle, subParams);

        loadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadProgress.setMax(100);
        loadProgress.setProgress(8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            loadProgress.setProgressTintList(ColorStateList.valueOf(Color.rgb(135, 76, 186)));
        }
        splash.addView(loadProgress, new LinearLayout.LayoutParams(dp(235), dp(5)));

        TextView loading = new TextView(this);
        loading.setText("Loading live dashboard…");
        loading.setTextColor(Color.rgb(150, 156, 165));
        loading.setTextSize(11);
        loading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        loadingParams.topMargin = dp(11);
        splash.addView(loading, loadingParams);
        return splash;
    }

    private Bitmap loadOfficialLogoBitmap() {
        try (InputStream input = getResources().openRawResource(R.raw.human_appeal_logo_b64);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder encoded = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) encoded.append(line.trim());
            byte[] bytes = Base64.decode(encoded.toString(), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void hideSplash() {
        if (dashboardReady && (splashView == null || splashView.getVisibility() != View.VISIBLE)) return;
        dashboardReady = true;
        if (loadProgress != null) loadProgress.setProgress(100);
        if (splashView != null && splashView.getVisibility() == View.VISIBLE) {
            splashView.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(() -> splashView.setVisibility(View.GONE))
                    .start();
        }
        if (!servicesInitialized) {
            servicesInitialized = true;
            mainHandler.postDelayed(this::safeInitializeServices, 850);
        }
    }

    private void hideGoogleHostUi() {
        if (webView == null) return;
        String js = "(function(){try{" +
                "document.documentElement.style.margin='0';document.documentElement.style.padding='0';" +
                "document.body.style.margin='0';document.body.style.padding='0';" +
                "var f=document.querySelector('iframe');" +
                "if(f){f.style.setProperty('position','fixed','important');f.style.setProperty('inset','0','important');" +
                "f.style.setProperty('width','100vw','important');f.style.setProperty('height','100vh','important');" +
                "f.style.setProperty('border','0','important');f.style.setProperty('z-index','2147483647','important');}" +
                "var terms=['الإبلاغ عن إساءة استخدام','مزيد من المعلومات','Report abuse','More info'];" +
                "[].slice.call(document.querySelectorAll('div,header,section')).forEach(function(e){" +
                "var t=e.innerText||'';var r=e.getBoundingClientRect();" +
                "if(r.top<190&&r.height>20&&r.height<200&&((t.indexOf(terms[0])>=0&&t.indexOf(terms[1])>=0)||(t.indexOf(terms[2])>=0&&t.indexOf(terms[3])>=0)))" +
                "e.style.setProperty('display','none','important');});" +
                "}catch(e){}})();";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Throwable ignored) {}
    }

    private void showFallbackScreen(FrameLayout root) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        box.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Human Appeal Media Plan");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(34, 40, 50));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView message = new TextView(this);
        message.setText("Tap to open the live dashboard in your browser.");
        message.setTextSize(14);
        message.setTextColor(Color.rgb(120, 126, 136));
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(18), 0, 0);
        box.addView(message);
        box.setOnClickListener(v -> openDashboardExternally());
        root.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void openDashboardExternally() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DASHBOARD_URL)));
        } catch (Exception ignored) {
            Toast.makeText(this, "Unable to open dashboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void safeInitializeServices() {
        try { createNotificationChannel(this); } catch (Throwable ignored) {}
        try { requestNotificationPermissionIfNeeded(); } catch (Throwable ignored) {}
        try { scheduleBackgroundSync(); } catch (Throwable ignored) {}
    }

    private void safeEnableImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) safeEnableImmersiveMode();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1202);
        }
    }

    private void scheduleBackgroundSync() {
        JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || scheduler.getPendingJob(SYNC_JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(SYNC_JOB_ID,
                new ComponentName(this, DashboardSyncService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .build();
        scheduler.schedule(job);
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Plan updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Alerts when a new day of media-plan data is added.");
            manager.createNotificationChannel(channel);
        }
    }

    public static void processDashboardUpdate(Context context, String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject latest = root.optJSONObject("latest");
            if (latest == null) latest = root;

            String date = latest.optString("date", "");
            int day = latest.optInt("dayNumber", 0);
            double spend = latest.optDouble("spend", 0);
            double conversions = latest.optDouble("conversions", 0);
            double value = latest.optDouble("value", 0);
            if (date.isEmpty()) return;

            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String previousDay = preferences.getString(KEY_LAST_DAY, "");
            if (previousDay.isEmpty()) {
                preferences.edit().putString(KEY_LAST_DAY, date).apply();
                return;
            }
            if (!date.equals(previousDay)) {
                preferences.edit().putString(KEY_LAST_DAY, date).apply();
                showNewDayNotification(context, day, spend, conversions, value);
            }
        } catch (Exception ignored) {}
    }

    private static void showNewDayNotification(Context context, int day, double spend, double conversions, double value) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

            createNotificationChannel(context);
            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 1201, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String body = "أرقام يوم " + day + " اتضافت • Spend £" + format0(spend) +
                    " • Conv " + format0(conversions) + " • Value £" + format0(value);

            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);
            builder.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("Media Plan USA")
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, builder.build());
        } catch (Throwable ignored) {}
    }

    private static String format0(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void dashboardReady() {
            runOnUiThread(MainActivity.this::hideSplash);
        }

        @JavascriptInterface
        public void onDashboardData(String json) {
            new Thread(() -> processDashboardUpdate(getApplicationContext(), json)).start();
        }

        @JavascriptInterface
        public void shareImage(String dataUrl, String summary, String filename) {
            new Thread(() -> {
                try {
                    int comma = dataUrl.indexOf(',');
                    String payload = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
                    byte[] bytes = Base64.decode(payload.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
                    File file = writeShareBytes(bytes, filename);
                    runOnUiThread(() -> shareImageFile(file, summary));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            "Could not prepare the screenshot",
                            Toast.LENGTH_SHORT).show());
                }
            }).start();
        }

        @JavascriptInterface
        public void shareCurrentScreen(String summary, String filename) {
            runOnUiThread(() -> {
                try {
                    int width = Math.max(webView.getWidth(), 1);
                    int height = Math.max(webView.getHeight(), 1);
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    webView.draw(canvas);
                    File dir = new File(getCacheDir(), "shares");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, safeFilename(filename));
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    bitmap.recycle();
                    shareImageFile(file, summary);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not capture the screen", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private File writeShareBytes(byte[] bytes, String filename) throws Exception {
        File dir = new File(getCacheDir(), "shares");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create share folder");
        File file = new File(dir, safeFilename(filename));
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
        return file;
    }

    private String safeFilename(String filename) {
        String name = filename == null || filename.trim().isEmpty()
                ? "Media-Plan-USA.png" : filename;
        name = name.replaceAll("[^A-Za-z0-9._-]", "-");
        if (!name.toLowerCase(Locale.US).endsWith(".png")) name += ".png";
        return name;
    }

    private void shareImageFile(File file, String summary) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            if (summary != null && !summary.trim().isEmpty()) {
                send.putExtra(Intent.EXTRA_TEXT, summary);
            }
            send.setClipData(ClipData.newRawUri("Media Plan screenshot", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (isPackageInstalled("com.whatsapp")) {
                send.setPackage("com.whatsapp");
                startActivity(send);
            } else if (isPackageInstalled("com.whatsapp.w4b")) {
                send.setPackage("com.whatsapp.w4b");
                startActivity(send);
            } else {
                startActivity(Intent.createChooser(send, "Share Media Plan"));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open WhatsApp", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkForGitHubUpdate() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(LATEST_RELEASE_API);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Human-Appeal-Media-Plan-Android");
                if (connection.getResponseCode() != 200) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder text = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
                reader.close();

                JSONObject release = new JSONObject(text.toString());
                String latestTag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                String currentVersion = BuildConfig.VERSION_NAME;
                String apkUrl = null;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset != null && APK_ASSET_NAME.equals(asset.optString("name"))) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                if (apkUrl != null && isNewerVersion(latestTag, currentVersion)) {
                    String finalUrl = apkUrl;
                    runOnUiThread(() -> showUpdateDialog(latestTag, finalUrl));
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void showUpdateDialog(String version, String apkUrl) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("Human Appeal Media Plan " + version + " is available on GitHub.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Update", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)));
                    } catch (Exception e) {
                        Toast.makeText(this, "Unable to open GitHub download", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] a = latest.split("\\.");
            String[] b = current.split("\\.");
            int count = Math.max(a.length, b.length);
            for (int i = 0; i < count; i++) {
                int av = i < a.length ? Integer.parseInt(a[i].replaceAll("[^0-9]", "")) : 0;
                int bv = i < b.length ? Integer.parseInt(b[i].replaceAll("[^0-9]", "")) : 0;
                if (av > bv) return true;
                if (av < bv) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
