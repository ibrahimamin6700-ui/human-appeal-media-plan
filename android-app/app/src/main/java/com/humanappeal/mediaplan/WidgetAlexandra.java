package com.humanappeal.mediaplan;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class WidgetAlexandra extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetKit.update(context, manager, ids, WidgetAlexandra.class);
    }
}
