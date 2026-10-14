package com.humanappeal.mediaplan;

import android.app.JobParameters;
import android.app.JobService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DashboardSyncService extends JobService {
    private volatile boolean cancelled = false;

    @Override
    public boolean onStartJob(JobParameters params) {
        cancelled = false;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(MainActivity.DASHBOARD_URL + "?api=latest&t=" + System.currentTimeMillis());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(9000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "Human-Appeal-Media-Plan-Android");

                if (!cancelled && connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    reader.close();
                    if (!cancelled) MainActivity.processDashboardUpdate(getApplicationContext(), body.toString());
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        cancelled = true;
        return true;
    }
}
