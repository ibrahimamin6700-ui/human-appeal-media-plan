package com.humanappeal.mediaplan;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * While this device waits for access approval, checks the status in the background
 * (about every 15 minutes) and shows a SEKNA notification once access is granted.
 */
public class AccessApprovalWorker extends Worker {
    private static final String UNIQUE_WORK = "sekna-access-approval";
    private static final String KEY_TOKEN = "access_watch_token";

    public AccessApprovalWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void start(Context context, String token) {
        if (token == null || token.length() < 40) return;
        prefs(context).edit().putString(KEY_TOKEN, token).apply();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AccessApprovalWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInitialDelay(2, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void stop(Context context) {
        prefs(context).edit().remove(KEY_TOKEN).apply();
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(HumanAppealApp.PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String token = prefs(context).getString(KEY_TOKEN, null);
        if (token == null) {
            stop(context);
            return Result.success();
        }
        try {
            JSONObject status = fetchStatus(token);
            String value = status.optString("status", "");
            if ("Approved".equals(value)) {
                String name = status.optString("name", "").trim();
                String first = name.isEmpty() ? "" : name.split("\\s+")[0];
                SeknaNotifications.show(context, SeknaNotifications.ACCESS_ID,
                        first.isEmpty() ? "You're in!" : "You're in, " + first + "!",
                        "Your HEMERA access has been approved. Tap to open your dashboard.", false);
                stop(context);
            } else if (!"Pending".equals(value) && !value.isEmpty()) {
                stop(context);
            }
        } catch (Exception ignored) {
            // Network problems: try again at the next run.
        }
        return Result.success();
    }

    /** Asks the SEKNA server first; Apps Script answers only if the server cannot be reached. */
    private static JSONObject fetchStatus(String token) throws Exception {
        try {
            return fetchServerStatus(token);
        } catch (Exception e) {
            return fetchAppsScriptStatus(token);
        }
    }

    private static JSONObject fetchServerStatus(String token) throws Exception {
        JSONObject body = new JSONObject();
        body.put("fn", "accessStatus");
        body.put("args", new org.json.JSONArray().put(token));
        HttpURLConnection post = (HttpURLConnection) new URL(MainActivity.SERVER_API_URL + "/call").openConnection();
        post.setConnectTimeout(15000);
        post.setReadTimeout(20000);
        post.setRequestMethod("POST");
        post.setDoOutput(true);
        post.setRequestProperty("Content-Type", "text/plain");
        try (OutputStream out = post.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(post.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) text.append(line);
            JSONObject answer = new JSONObject(text.toString());
            if (!answer.optBoolean("ok", false) || answer.optJSONObject("result") == null) throw new Exception("No answer");
            return answer.getJSONObject("result");
        } finally {
            post.disconnect();
        }
    }

    private static JSONObject fetchAppsScriptStatus(String token) throws Exception {
        JSONObject body = new JSONObject();
        body.put("action", "status");
        body.put("token", token);

        HttpURLConnection post = (HttpURLConnection) new URL(MainActivity.DASHBOARD_URL).openConnection();
        post.setInstanceFollowRedirects(false);
        post.setConnectTimeout(15000);
        post.setReadTimeout(20000);
        post.setRequestMethod("POST");
        post.setDoOutput(true);
        post.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = post.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = post.getResponseCode();
        HttpURLConnection reader = post;
        // Apps Script answers a POST with a redirect to the result, which is read with GET.
        if (code >= 300 && code < 400) {
            String location = post.getHeaderField("Location");
            post.disconnect();
            if (location == null) throw new Exception("No redirect location");
            reader = (HttpURLConnection) new URL(location).openConnection();
            reader.setConnectTimeout(15000);
            reader.setReadTimeout(20000);
        }
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(reader.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) text.append(line);
            return new JSONObject(text.toString());
        } finally {
            reader.disconnect();
        }
    }
}
