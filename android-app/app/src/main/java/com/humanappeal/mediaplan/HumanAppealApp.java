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
    public static final String KEY_APP_LOCK = "app_lock_enabled";
    public static final String KEY_USER_TOPIC = "user_push_topic";

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

    public static boolean appLockEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_APP_LOCK, false);
    }

    public static void setAppLockEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_APP_LOCK, enabled)
                .apply();
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
            String userTopic = userTopic(context);
            if (notificationsEnabled(context)) {
                FirebaseMessaging.getInstance().subscribeToTopic(FCM_TOPIC);
                if (!userTopic.isEmpty()) FirebaseMessaging.getInstance().subscribeToTopic(userTopic);
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(FCM_TOPIC);
                if (!userTopic.isEmpty()) FirebaseMessaging.getInstance().unsubscribeFromTopic(userTopic);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String userTopic(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_TOPIC, "");
    }

    /** The signed-in person's own topic ("sekna-u-…"), so Team Tasks notices reach only them. Empty on sign-out. */
    public static void setUserTopic(Context context, String topic) {
        String safe = topic != null && topic.matches("sekna-u-[0-9a-f]{24}") ? topic : "";
        String old = userTopic(context);
        if (safe.equals(old)) {
            applyTopicSubscription(context);
            return;
        }
        try {
            if (!old.isEmpty()) FirebaseMessaging.getInstance().unsubscribeFromTopic(old);
        } catch (Throwable ignored) {
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_USER_TOPIC, safe).apply();
        applyTopicSubscription(context);
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
