package com.humanappeal.mediaplan;

import android.app.Application;
import android.content.Context;

import com.google.firebase.messaging.FirebaseMessaging;

public class HumanAppealApp extends Application {
    public static final String FCM_TOPIC = "media-plan-updates";
    public static final String PREFS = "media_plan_prefs";
    public static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    public static final String KEY_NOTIFICATION_SOUND = "notification_sound";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_LAST_ACCOUNT = "last_account";

    @Override
    public void onCreate() {
        super.onCreate();
        applyTopicSubscription(this);
    }

    public static boolean notificationsEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public static String notificationSound(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_NOTIFICATION_SOUND, "default");
    }

    public static boolean darkMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE, false);
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
                .apply();
        applyTopicSubscription(context);
    }

    public static void setNotificationSound(Context context, String sound) {
        String safe = sanitizeSound(sound);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NOTIFICATION_SOUND, safe)
                .apply();
    }

    public static void setDarkMode(Context context, boolean dark) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DARK_MODE, dark)
                .apply();
    }

    public static void setLastAccount(Context context, String accountKey) {
        String safe = "ar".equalsIgnoreCase(accountKey) ? "ar" : "usa";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_ACCOUNT, safe)
                .apply();
    }

    public static void applyTopicSubscription(Context context) {
        try {
            if (notificationsEnabled(context)) {
                FirebaseMessaging.getInstance().subscribeToTopic(FCM_TOPIC);
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(FCM_TOPIC);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String sanitizeSound(String sound) {
        if ("human_appeal".equals(sound) ||
                "gentle_bell".equals(sound) ||
                "soft_ping".equals(sound)) {
            return sound;
        }
        return "default";
    }
}
