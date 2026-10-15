package com.humanappeal.mediaplan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    public static final String CHANNEL_ID = "media_plan_updates_v2";
    public static final String EXTRA_OPEN_ACCOUNT = "open_account";

    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/ibrahimamin6700-ui/human-appeal-media-plan/releases/latest";
    private static final String APK_ASSET_NAME = "Human-Appeal-Media-Plan.apk";
    private static final int LEGACY_SYNC_JOB_ID = 12026;
    private static final int SPLASH_BACKGROUND = Color.rgb(254, 254, 254);
    // Positions inside the SEKNA splash artwork, as fractions of its width/height.
    private static final float SPLASH_WORDMARK_WIDTH = 0.46f;
    private static final float SPLASH_BAR_TOP = 0.585f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout rootView;
    private WebView webView;
    private SeknaProgressBar loadProgress;
    private TextView loadingText;
    private View splashView;
    private SeknaOfflineView offlineView;
    private boolean dashboardReady = false;
    private boolean mainFrameLoadFailed = false;
    private boolean servicesInitialized = false;
    private long splashStartedAt = 0L;
    private String pendingOpenAccount = null;

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pendingOpenAccount = normalizeAccount(
                getIntent() == null ? null : getIntent().getStringExtra(EXTRA_OPEN_ACCOUNT));
        applySystemBars(HumanAppealApp.darkMode(this));

        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(Color.BLACK);

        try {
            webView = new WebView(this);
        } catch (Throwable error) {
            showFallbackScreen(rootView);
            setContentView(rootView);
            return;
        }

        webView.setVisibility(View.VISIBLE);
        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        rootView.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        splashView = buildSplashView();
        rootView.addView(splashView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(rootView);
        splashStartedAt = System.currentTimeMillis();

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
                if (loadProgress != null && splashView != null && splashView.getVisibility() == View.VISIBLE) {
                    int shown = Math.max(7, Math.min(progress, 96));
                    loadProgress.setProgress(shown);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (!"about:blank".equalsIgnoreCase(url)) mainFrameLoadFailed = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if ("about:blank".equalsIgnoreCase(url)) return;
                if (mainFrameLoadFailed) {
                    showOfflineScreen();
                    return;
                }

                webView.setVisibility(View.VISIBLE);
                hideOfflineScreen();
                hideGoogleHostUi();
                mainHandler.postDelayed(MainActivity.this::hideGoogleHostUi, 250);
                mainHandler.postDelayed(MainActivity.this::hideGoogleHostUi, 900);
                mainHandler.postDelayed(MainActivity.this::applyPendingAccount, 1150);
                mainHandler.postDelayed(() -> {
                    if (!dashboardReady) hideSplash();
                }, 6000);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    mainFrameLoadFailed = true;
                    try { view.stopLoading(); } catch (Throwable ignored) {}
                    showOfflineScreen();
                    try { view.loadUrl("about:blank"); } catch (Throwable ignored) {}
                    return;
                }
                super.onReceivedError(view, request, error);
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
            mainFrameLoadFailed = false;
            webView.loadUrl(DASHBOARD_URL);
        } else {
            try {
                webView.restoreState(savedInstanceState);
            } catch (Throwable ignored) {
                mainFrameLoadFailed = false;
                webView.loadUrl(DASHBOARD_URL);
            }
        }

        mainHandler.postDelayed(this::checkForGitHubUpdate, 6500);
    }

    private View buildSplashView() {
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(SPLASH_BACKGROUND);

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.sekna_splash);
        image.setBackgroundColor(SPLASH_BACKGROUND);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splash.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        loadProgress = new SeknaProgressBar(this);
        loadProgress.setProgress(7);
        loadProgress.setElevation(dp(6));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(220), dp(12));
        progressParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        splash.addView(loadProgress, progressParams);

        loadingText = new TextView(this);
        loadingText.setText("Loading   ");
        loadingText.setTextSize(13);
        loadingText.setLetterSpacing(0.08f);
        loadingText.setTextColor(SeknaProgressBar.NAVY);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        splash.addView(loadingText, textParams);

        // Entrance: artwork fades in with a gentle zoom, then the bar and label rise in.
        image.setAlpha(0f);
        image.setScaleX(1.06f);
        image.setScaleY(1.06f);
        image.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(750)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        for (View v : new View[]{loadProgress, loadingText}) {
            v.setAlpha(0f);
            v.setTranslationY(dp(12));
            v.animate().alpha(1f).translationY(0f).setStartDelay(350).setDuration(500).start();
        }

        // Place the bar just under the tagline, matching the SEKNA wordmark width.
        splash.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = r - l;
            int height = b - t;
            if (width <= 0 || height <= 0 || image.getDrawable() == null) return;
            float iw = image.getDrawable().getIntrinsicWidth();
            float ih = image.getDrawable().getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) return;
            float scale = Math.max(width / iw, height / ih);
            int barWidth = Math.round(SPLASH_WORDMARK_WIDTH * iw * scale);
            barWidth = Math.max(dp(180), Math.min(barWidth, Math.min(dp(300), width - dp(48))));
            int barTop = Math.round((SPLASH_BAR_TOP - 0.5f) * ih * scale + height / 2f);

            FrameLayout.LayoutParams bar = (FrameLayout.LayoutParams) loadProgress.getLayoutParams();
            FrameLayout.LayoutParams label = (FrameLayout.LayoutParams) loadingText.getLayoutParams();
            if (bar.width == barWidth && bar.topMargin == barTop) return;
            bar.width = barWidth;
            bar.topMargin = barTop;
            label.topMargin = barTop + bar.height + dp(12);
            splash.post(() -> {
                loadProgress.setLayoutParams(bar);
                loadingText.setLayoutParams(label);
            });
        });

        mainHandler.post(loadingDots);
        return splash;
    }

    private final Runnable loadingDots = new Runnable() {
        private int dots = 0;

        @Override
        public void run() {
            if (loadingText == null || splashView == null || splashView.getVisibility() != View.VISIBLE) return;
            dots = (dots + 1) % 4;
            loadingText.setText("Loading" + "...".substring(0, dots) + "   ".substring(dots));
            mainHandler.postDelayed(this, 420);
        }
    };

    private void hideSplash() {
        if (dashboardReady && (splashView == null || splashView.getVisibility() != View.VISIBLE)) return;

        long elapsed = System.currentTimeMillis() - splashStartedAt;
        if (elapsed < 650) {
            mainHandler.postDelayed(this::hideSplash, 650 - elapsed);
            return;
        }

        dashboardReady = true;
        if (loadProgress != null) loadProgress.setProgress(100);

        if (splashView != null && splashView.getVisibility() == View.VISIBLE) {
            splashView.animate()
                    .alpha(0f)
                    .setDuration(380)
                    .withEndAction(() -> splashView.setVisibility(View.GONE))
                    .start();
        }

        if (!servicesInitialized) {
            servicesInitialized = true;
            mainHandler.postDelayed(this::safeInitializeServices, 500);
        }

        mainHandler.postDelayed(this::applyPendingAccount, 250);
    }

    private void showOfflineScreen() {
        runOnUiThread(() -> {
            if (rootView == null) return;
            if (splashView != null) {
                splashView.animate().cancel();
                splashView.setAlpha(1f);
                splashView.setVisibility(View.GONE);
            }
            if (webView != null) webView.setVisibility(View.INVISIBLE);

            if (offlineView == null) {
                offlineView = new SeknaOfflineView(this, this::retryDashboard);
                offlineView.setVisibility(View.GONE);
                rootView.addView(offlineView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }
            if (offlineView.getVisibility() == View.VISIBLE) {
                offlineView.retryFailed();
            } else {
                offlineView.setVisibility(View.VISIBLE);
                offlineView.setAlpha(1f);
                offlineView.show();
            }
            offlineView.bringToFront();
        });
    }

    private void hideOfflineScreen() {
        if (offlineView != null) {
            offlineView.hide();
            offlineView.setVisibility(View.GONE);
        }
    }

    private void retryDashboard() {
        if (webView == null) return;
        try {
            mainFrameLoadFailed = false;
            webView.stopLoading();
            webView.loadUrl(DASHBOARD_URL);
        } catch (Throwable ignored) {}
    }

    private void hideGoogleHostUi() {
        if (webView == null) return;
        String js = "(function(){try{" +
                "document.documentElement.style.margin='0';document.documentElement.style.padding='0';" +
                "document.body.style.margin='0';document.body.style.padding='0';" +
                "var f=document.querySelector('iframe');" +
                "if(f){f.style.setProperty('position','fixed','important');f.style.setProperty('inset','0','important');" +
                "f.style.setProperty('width','100%','important');f.style.setProperty('height','100%','important');" +
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
        title.setText("SEKNA");
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
        try { cancelLegacyBackgroundSync(); } catch (Throwable ignored) {}
        try { createNotificationChannel(this); } catch (Throwable ignored) {}
        try { HumanAppealApp.applyTopicSubscription(this); } catch (Throwable ignored) {}
        try {
            if (HumanAppealApp.notificationsEnabled(this)) requestNotificationPermissionIfNeeded();
        } catch (Throwable ignored) {}
    }

    private void cancelLegacyBackgroundSync() {
        JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(LEGACY_SYNC_JOB_ID);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1202);
        }
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Plan updates",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Human Appeal USA and AR media-plan updates.");
            channel.enableVibration(true);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }
    }

    public static void processDashboardUpdate(Context context, String json) {
        // Legacy polling intentionally disabled. Firebase is the single notification source.
    }

    /**
     * Matches the phone's status and navigation bars to the dashboard theme:
     * white bars with dark icons in light mode, near-black bars in dark mode.
     * Android 7 cannot draw dark navigation icons, so it keeps a black nav bar.
     */
    private void applySystemBars(boolean dark) {
        try {
            boolean lightNavIcons = !dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
            getWindow().setStatusBarColor(dark ? Color.rgb(11, 13, 17) : Color.WHITE);
            getWindow().setNavigationBarColor(dark ? Color.rgb(5, 6, 8)
                    : lightNavIcons ? Color.rgb(245, 246, 248) : Color.BLACK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(true);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
                }
            } else {
                int flags = 0;
                if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (lightNavIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {}
    }

    private String normalizeAccount(String account) {
        if (account == null) return null;
        return "ar".equalsIgnoreCase(account) ? "ar" : "usa";
    }

    private void applyPendingAccount() {
        if (webView == null || pendingOpenAccount == null) return;
        final String account = pendingOpenAccount;
        pendingOpenAccount = null;
        HumanAppealApp.setLastAccount(this, account);
        String js = "(function(){try{if(typeof switchAccount==='function'){switchAccount('" + account + "');}" +
                "else{localStorage.setItem('ha_media_plan_account','" + account + "');}}catch(e){}})();";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Throwable ignored) {}
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void dashboardReady() {
            runOnUiThread(MainActivity.this::hideSplash);
        }

        @JavascriptInterface
        public void onDashboardData(String json) {
            try {
                JSONObject root = new JSONObject(json);
                String account = root.optString("accountKey", "");
                if ("ar".equalsIgnoreCase(account) || "usa".equalsIgnoreCase(account)) {
                    HumanAppealApp.setLastAccount(getApplicationContext(), account);
                }
            } catch (Throwable ignored) {}
        }

        @JavascriptInterface
        public void setNotificationsEnabled(boolean enabled) {
            HumanAppealApp.setNotificationsEnabled(getApplicationContext(), enabled);
            if (enabled) {
                runOnUiThread(MainActivity.this::requestNotificationPermissionIfNeeded);
            }
        }

        @JavascriptInterface
        public void setNotificationSound(String sound) {
            HumanAppealApp.setNotificationSound(getApplicationContext(), sound);
        }

        @JavascriptInterface
        public void previewNotificationSound(String sound) {
            HumanAppealFirebaseMessagingService.playSelectedSound(getApplicationContext(), sound);
        }

        @JavascriptInterface
        public void setDarkMode(boolean dark) {
            HumanAppealApp.setDarkMode(getApplicationContext(), dark);
            runOnUiThread(() -> applySystemBars(dark));
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
                ? "Media-Plan.png" : filename;
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
        }, "ha-update-check").start();
    }

    private void showUpdateDialog(String version, String apkUrl) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("SEKNA " + version + " is available.")
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
    protected void onResume() {
        super.onResume();
        applySystemBars(HumanAppealApp.darkMode(this));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingOpenAccount = normalizeAccount(
                intent == null ? null : intent.getStringExtra(EXTRA_OPEN_ACCOUNT));
        if (dashboardReady) mainHandler.postDelayed(this::applyPendingAccount, 150);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (offlineView != null && offlineView.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
