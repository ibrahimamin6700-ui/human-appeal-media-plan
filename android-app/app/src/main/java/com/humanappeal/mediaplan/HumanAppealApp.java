package com.humanappeal.mediaplan;

import android.app.Application;

import com.google.firebase.messaging.FirebaseMessaging;

public class HumanAppealApp extends Application {
    public static final String FCM_TOPIC = "media-plan-updates";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(FCM_TOPIC);
        } catch (Throwable ignored) {
        }
    }
}
