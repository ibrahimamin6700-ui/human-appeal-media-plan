package com.humanappeal.mediaplan;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Alexandra's face with yesterday's number; a tap starts a voice call with her. */
public class WidgetCall extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetKit.update(context, manager, ids, WidgetCall.class);
    }
}
