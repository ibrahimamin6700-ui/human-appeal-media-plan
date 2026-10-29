package com.humanappeal.mediaplan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
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
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
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
    // The app shows the SEKNA web app, so design changes reach the phone without an app update.
    // SERVER_API_URL is the SEKNA server the background workers ask; DASHBOARD_URL (Apps Script) is its backup.
    public static final String WEB_APP_URL = "https://sekna-app.web.app/";
    public static final String SERVER_API_URL = "https://europe-west2-human-appeal-media-plan.cloudfunctions.net/api";
    public static final String DASHBOARD_URL = "https://script.google.com/macros/s/AKfycbzjgZ1nXxLTcFdeFCIEznOj2H0XCo9YGUeu1YF2pJdsQiOo_aM-Mn6HxKKNk8peEuM1Ig/exec";
    public static final String CHANNEL_ID = "media_plan_updates_v2";
    public static final String EXTRA_OPEN_ACCOUNT = "open_account";

    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/ibrahimamin6700-ui/human-appeal-media-plan/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/ibrahimamin6700-ui/human-appeal-media-plan/releases/latest";
    // The update itself downloads from the HEMERA website (lighter than GitHub); GitHub only tells which version is out.
    private static final String WEB_APK = "https://sekna-app.web.app/get/HEMERA.apk";
    private static final String APK_ASSET_NAME = "HEMERA.apk";
    private static final String OLD_APK_ASSET_NAME = "SEKNA.apk";
    private static final String LEGACY_APK_ASSET_NAME = "Human-Appeal-Media-Plan.apk";
    private static final int UNLOCK_REQUEST = 1302;
    // SEKNA locks again after this long in the background.
    private static final long LOCK_AFTER_MS = 30_000L;
    private static final int LEGACY_SYNC_JOB_ID = 12026;
    private static final int FILE_CHOOSER_REQUEST = 1301;
    // The SEKNA splash lives in the dashboard. Until its page draws, the app shows the same
    // glow and logo, then hands over to it without a visible change.
    private static final int SPLASH_CENTER = Color.rgb(44, 86, 214);
    private static final int SPLASH_MID = Color.rgb(23, 51, 143);
    private static final int SPLASH_EDGE = Color.rgb(10, 23, 72);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout rootView;
    private WebView webView;
    private View splashView;
    private SeknaOfflineView offlineView;
    private boolean dashboardReady = false;
    private boolean mainFrameLoadFailed = false;
    private boolean servicesInitialized = false;
    private String pendingOpenAccount = null;
    private ValueCallback<Uri[]> filePathCallback;
    private SeknaLockView lockView;
    private boolean authInProgress = false;
    private long backgroundedAt = 0L;
    private Runnable authSuccess;
    private Runnable authFailure;
    private boolean updateDialogShown = false;

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pendingOpenAccount = normalizeAccount(
                getIntent() == null ? null : getIntent().getStringExtra(EXTRA_OPEN_ACCOUNT));
        pendingOpenTasks = getIntent() != null && getIntent().getBooleanExtra(SeknaNotifications.EXTRA_OPEN_TASKS, false);
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
        webView.setBackgroundColor(HumanAppealApp.darkMode(this) ? Color.rgb(5, 6, 8) : Color.rgb(244, 246, 251));
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
        if (HumanAppealApp.appLockEnabled(this)) {
            showLock();
            mainHandler.postDelayed(this::requestUnlock, 450);
        }

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

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidShare");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Unable to open photos", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
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
                // Only the old Apps Script page has Google's frame to tidy; on the SEKNA web app this
                // scan of every block re-laid out the page three times and made it stutter on open.
                if (isAppsScriptPage(url)) {
                    hideGoogleHostUi();
                    mainHandler.postDelayed(MainActivity.this::hideGoogleHostUi, 250);
                    mainHandler.postDelayed(MainActivity.this::hideGoogleHostUi, 900);
                }
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
                if (host.equals("sekna-app.web.app") || host.equals("sekna-app.firebaseapp.com")
                        || host.endsWith("google.com") || host.endsWith("googleusercontent.com")) {
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
            webView.loadUrl(WEB_APP_URL);
        } else {
            try {
                webView.restoreState(savedInstanceState);
            } catch (Throwable ignored) {
                mainFrameLoadFailed = false;
                webView.loadUrl(WEB_APP_URL);
            }
        }

        boolean openedForUpdate = getIntent() != null
                && getIntent().getBooleanExtra(SeknaNotifications.EXTRA_CHECK_UPDATE, false);
        mainHandler.postDelayed(() -> checkForGitHubUpdate(false), openedForUpdate ? 2500 : 6500);
    }

    /* ---------- App lock (fingerprint, face or phone PIN) ---------- */

    private void showLock() {
        if (rootView == null) return;
        if (lockView == null) {
            lockView = new SeknaLockView(this, this::requestUnlock);
            rootView.addView(lockView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        lockView.setVisibility(View.VISIBLE);
        lockView.bringToFront();
    }

    private void hideLock() {
        if (lockView != null) lockView.setVisibility(View.GONE);
    }

    private boolean isLocked() {
        return lockView != null && lockView.getVisibility() == View.VISIBLE;
    }

    private boolean deviceIsSecure() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isDeviceSecure();
    }

    private void requestUnlock() {
        if (!isLocked() || authInProgress) return;
        if (!deviceIsSecure()) {
            // The phone no longer has a screen lock, so the app lock cannot work: turn it off.
            HumanAppealApp.setAppLockEnabled(this, false);
            hideLock();
            return;
        }
        authenticate("Unlock HEMERA", this::hideLock, null);
    }

    /** Asks for the fingerprint / face, with the phone PIN, pattern or password as a fallback. */
    private void authenticate(String title, Runnable onSuccess, Runnable onFailure) {
        authSuccess = onSuccess;
        authFailure = onFailure;
        authInProgress = true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this)
                        .setTitle(title)
                        .setSubtitle("Use your fingerprint, face or phone PIN");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                } else {
                    builder.setDeviceCredentialAllowed(true);
                }
                builder.build().authenticate(new CancellationSignal(), getMainExecutor(),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                                finishAuth(true);
                            }

                            @Override
                            public void onAuthenticationError(int errorCode, CharSequence errString) {
                                finishAuth(false);
                            }
                        });
            } else {
                KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                Intent intent = keyguard == null ? null
                        : keyguard.createConfirmDeviceCredentialIntent(title, "Use your phone PIN, pattern or password");
                if (intent == null) {
                    finishAuth(true);
                } else {
                    startActivityForResult(intent, UNLOCK_REQUEST);
                }
            }
        } catch (Throwable error) {
            finishAuth(false);
        }
    }

    private void finishAuth(boolean success) {
        authInProgress = false;
        backgroundedAt = 0L;
        Runnable next = success ? authSuccess : authFailure;
        authSuccess = null;
        authFailure = null;
        if (next != null) runOnUiThread(next);
    }

    private void reportAppLock(boolean enabled, String message) {
        try {
            String js = "(function(){try{if(window.onSeknaAppLock)window.onSeknaAppLock(" + enabled + ","
                    + (message == null ? "null" : JSONObject.quote(message)) + ");}catch(e){}})();";
            runOnUiThread(() -> {
                if (webView != null) webView.evaluateJavascript(js, null);
            });
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (backgroundedAt > 0 && !authInProgress && HumanAppealApp.appLockEnabled(this)
                && System.currentTimeMillis() - backgroundedAt >= LOCK_AFTER_MS) {
            showLock();
            mainHandler.postDelayed(this::requestUnlock, 250);
        }
        backgroundedAt = 0L;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!authInProgress && !isChangingConfigurations()) backgroundedAt = System.currentTimeMillis();
    }

    // Same layout as the dashboard splash: a beating gold glow, the white SEKNA logo, the
    // tagline and an empty bar, so the logo is on screen at once and the hand-over is seamless.
    private View buildSplashView() {
        FrameLayout splash = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{SPLASH_CENTER, SPLASH_MID, SPLASH_EDGE});
        bg.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientCenter(0.5f, 0.46f);
        bg.setGradientRadius(dp(420));
        splash.setBackground(bg);

        View glow = new View(this);
        android.graphics.drawable.GradientDrawable gold = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(140, 255, 226, 160), Color.argb(31, 255, 226, 160), Color.TRANSPARENT});
        gold.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
        gold.setGradientRadius(dp(150));
        gold.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        glow.setBackground(gold);
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(dp(300), dp(300));
        glowParams.gravity = Gravity.CENTER;
        splash.addView(glow, glowParams);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_HORIZONTAL);

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.sekna_logo_white);
        logo.setAdjustViewBounds(true);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tag = new TextView(this);
        tag.setText("DAY BY DAY");
        tag.setTextSize(12);
        tag.setLetterSpacing(0.32f);
        tag.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        tag.setTextColor(Color.rgb(201, 214, 255));
        tag.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tagParams.topMargin = dp(16);
        brand.addView(tag, tagParams);

        View track = new View(this);
        android.graphics.drawable.GradientDrawable trackBg = new android.graphics.drawable.GradientDrawable();
        trackBg.setColor(Color.argb(41, 255, 255, 255));
        trackBg.setCornerRadius(dp(3));
        track.setBackground(trackBg);
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(dp(200), dp(6));
        trackParams.topMargin = dp(28);
        brand.addView(track, trackParams);

        TextView label = new TextView(this);
        label.setText("LOADING");
        label.setTextSize(11);
        label.setLetterSpacing(0.32f);
        label.setTextColor(Color.argb(166, 207, 217, 255));
        label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        label.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(14);
        brand.addView(label, labelParams);

        FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        brandParams.gravity = Gravity.CENTER;
        splash.addView(brand, brandParams);

        // The web splash sits 4% above the middle; the glow is centred at 46% of the height.
        splash.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int height = b - t;
            int size = Math.max(r - l, height);
            if (size > 0) bg.setGradientRadius(size * 0.75f);
            brand.setTranslationY(-0.04f * height);
            glow.setTranslationY(-0.04f * height);
        });

        // Entrance: the logo bursts in, then the glow keeps beating like a heart.
        brand.setAlpha(0f);
        brand.setScaleX(0.8f);
        brand.setScaleY(0.8f);
        brand.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        android.animation.ObjectAnimator beat = android.animation.ObjectAnimator.ofPropertyValuesHolder(glow,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f, 1f, 1.07f, 1f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f, 1f, 1.07f, 1f));
        beat.setDuration(1200);
        beat.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        beat.start();
        return splash;
    }

    // The dashboard's own splash is on screen: drop the native cover at once.
    private void hideSplashCover() {
        if (splashView != null && splashView.getVisibility() == View.VISIBLE) {
            splashView.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> splashView.setVisibility(View.GONE)).start();
        }
    }

    private void hideSplash() {
        if (dashboardReady && (splashView == null || splashView.getVisibility() != View.VISIBLE)) return;

        dashboardReady = true;

        if (splashView != null && splashView.getVisibility() == View.VISIBLE) {
            splashView.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(() -> splashView.setVisibility(View.GONE))
                    .start();
        }

        if (!servicesInitialized) {
            servicesInitialized = true;
            mainHandler.postDelayed(this::safeInitializeServices, 500);
        }

        mainHandler.postDelayed(this::applyPendingAccount, 250);
        mainHandler.postDelayed(this::applyPendingTasks, 1200);
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
            webView.loadUrl(WEB_APP_URL);
        } catch (Throwable ignored) {}
    }

    private static boolean isAppsScriptPage(String url) {
        String u = url == null ? "" : url.toLowerCase();
        return u.contains("script.google.com") || u.contains("googleusercontent.com");
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
        title.setText("HEMERA");
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
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WEB_APP_URL)));
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
        public void splashShown() {
            runOnUiThread(MainActivity.this::hideSplashCover);
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
        public void setPushUser(String topic) {
            HumanAppealApp.setUserTopic(getApplicationContext(), topic);
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
        public boolean isAppLockEnabled() {
            return HumanAppealApp.appLockEnabled(getApplicationContext());
        }

        /** Turning the lock on or off always needs the fingerprint / PIN first. */
        @JavascriptInterface
        public void setAppLockEnabled(boolean enabled) {
            runOnUiThread(() -> {
                boolean current = HumanAppealApp.appLockEnabled(MainActivity.this);
                if (enabled && !deviceIsSecure()) {
                    reportAppLock(false, "Set a screen lock (PIN, pattern or fingerprint) on your phone first.");
                    return;
                }
                if (!enabled && !deviceIsSecure()) {
                    HumanAppealApp.setAppLockEnabled(MainActivity.this, false);
                    reportAppLock(false, "App lock is off");
                    return;
                }
                authenticate(enabled ? "Turn on app lock" : "Turn off app lock", () -> {
                    HumanAppealApp.setAppLockEnabled(MainActivity.this, enabled);
                    reportAppLock(enabled, enabled ? "App lock is on" : "App lock is off");
                }, () -> reportAppLock(current, null));
            });
        }

        @JavascriptInterface
        public void watchAccessApproval(String token) {
            try { AccessApprovalWorker.start(getApplicationContext(), token); } catch (Throwable ignored) {}
        }

        @JavascriptInterface
        public void stopAccessWatch() {
            try { AccessApprovalWorker.stop(getApplicationContext()); } catch (Throwable ignored) {}
        }

        @JavascriptInterface
        public String getAppVersion() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public void checkForAppUpdate() {
            checkForGitHubUpdate(true);
        }

        @JavascriptInterface
        public void openAppUpdate(String url) {
            String target = url != null && (url.startsWith("https://sekna-app.web.app/") || url.startsWith("https://github.com/")) ? url : WEB_APK;
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Unable to open the download", Toast.LENGTH_SHORT).show();
                }
            });
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

    /**
     * Checks GitHub Releases for a newer signed APK. When the dashboard asks
     * (manual = true) the result is also sent back to the page.
     */
    private void checkForGitHubUpdate(boolean manual) {
        new Thread(() -> {
            String result = "error";
            String latestShown = "";
            String pageUrl = WEB_APK;
            String apkFound = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(LATEST_RELEASE_API);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "HEMERA-Android");
                int code = connection.getResponseCode();
                if (code == 404) result = "latest"; // no official release published yet
                if (code != 200) return;

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
                        String assetName = asset == null ? "" : asset.optString("name");
                        if (APK_ASSET_NAME.equals(assetName) || OLD_APK_ASSET_NAME.equals(assetName) || LEGACY_APK_ASSET_NAME.equals(assetName)) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                latestShown = latestTag;
                if (apkUrl != null && isNewerVersion(latestTag, currentVersion)) {
                    // The dashboard shows the update in its own window; see reportUpdateResult.
                    result = "available";
                    apkFound = apkUrl;
                } else {
                    result = "latest";
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
                if (manual || "available".equals(result)) {
                    reportUpdateResult(result, latestShown, pageUrl, apkFound, !manual);
                }
            }
        }, "ha-update-check").start();
    }

    // Sends the update check to the dashboard. If the page cannot show it (an older page,
    // or the sign-in screen is open) the plain system prompt is the fallback.
    private void reportUpdateResult(String status, String latest, String pageUrl, String apkUrl, boolean auto) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("status", status);
            payload.put("latest", latest);
            payload.put("current", BuildConfig.VERSION_NAME);
            payload.put("url", pageUrl);
            payload.put("auto", auto);
            String js = "(function(){try{if(window.onSeknaAppUpdate)return window.onSeknaAppUpdate(" + payload + ")===true;}catch(e){}return false;})();";
            runOnUiThread(() -> {
                if (webView == null) {
                    if ("available".equals(status) && apkUrl != null) showUpdateDialog(latest, apkUrl);
                    return;
                }
                webView.evaluateJavascript(js, value -> {
                    if (!"true".equals(value) && "available".equals(status) && apkUrl != null) {
                        showUpdateDialog(latest, apkUrl);
                    }
                });
            });
        } catch (Exception ignored) {
        }
    }

    // Update prompt in the SEKNA look: navy card, white logo, gold button.
    private void showUpdateDialog(String version, String apkUrl) {
        if (isFinishing() || updateDialogShown) return;
        updateDialogShown = true;
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(26), dp(24), dp(20));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#2F50B5"), Color.parseColor("#152A66"), Color.parseColor("#0C1A46")});
        bg.setCornerRadius(dp(24));
        card.setBackground(bg);

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.sekna_logo_white);
        logo.setAdjustViewBounds(true);
        card.addView(logo, new LinearLayout.LayoutParams(dp(140), dp(36)));

        TextView badge = new TextView(this);
        badge.setText("\u2193");
        badge.setTextSize(20);
        badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badge.setTextColor(Color.parseColor("#0C1A46"));
        badge.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable gold = new android.graphics.drawable.GradientDrawable();
        gold.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        gold.setColor(Color.parseColor("#E2B874"));
        badge.setBackground(gold);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(44), dp(44));
        bp.topMargin = dp(18);
        card.addView(badge, bp);

        TextView title = new TextView(this);
        title.setText("HEMERA " + version + " is ready");
        title.setTextSize(19);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(14);
        card.addView(title, tp);

        TextView body = new TextView(this);
        body.setText("You have " + BuildConfig.VERSION_NAME + ". HEMERA.apk downloads from the HEMERA website; open it to install.");
        body.setTextSize(13);
        body.setTextColor(Color.parseColor("#C9D3F5"));
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(6);
        card.addView(body, mp);

        TextView update = new TextView(this);
        update.setText("Update to " + version);
        update.setTextSize(15);
        update.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        update.setTextColor(Color.parseColor("#0C1A46"));
        update.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable btn = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#E2B874"), Color.parseColor("#F3D79F")});
        btn.setCornerRadius(dp(14));
        update.setBackground(btn);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-1, dp(50));
        up.topMargin = dp(20);
        card.addView(update, up);

        TextView later = new TextView(this);
        later.setText("Later");
        later.setTextSize(14);
        later.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        later.setTextColor(Color.parseColor("#C9D3F5"));
        later.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(4);
        card.addView(later, lp);

        update.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WEB_APK)));
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open the download", Toast.LENGTH_SHORT).show();
            }
        });
        later.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(card);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(48), dp(360)), -2);
        }
        dialog.setOnDismissListener(d -> updateDialogShown = false);
        dialog.show();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UNLOCK_REQUEST) {
            finishAuth(resultCode == RESULT_OK);
            return;
        }
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
        if (intent != null && intent.getBooleanExtra(SeknaNotifications.EXTRA_CHECK_UPDATE, false)) {
            mainHandler.postDelayed(() -> checkForGitHubUpdate(false), 600);
        }
        if (intent != null && intent.getBooleanExtra(SeknaNotifications.EXTRA_OPEN_TASKS, false)) {
            pendingOpenTasks = true;
            if (dashboardReady) mainHandler.postDelayed(this::applyPendingTasks, 300);
        }
    }

    private boolean pendingOpenTasks = false;

    /** A Team Tasks notification opens My Tasks once the dashboard is ready. */
    private void applyPendingTasks() {
        if (webView == null || !pendingOpenTasks) return;
        pendingOpenTasks = false;
        try {
            webView.evaluateJavascript("(function(){try{if(typeof openTasks==='function')openTasks('mine');}catch(e){}})();", null);
        } catch (Throwable ignored) {}
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
