package com.humanappeal.mediaplan;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class WidgetClock extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetKit.update(context, manager, ids, WidgetClock.class);
        WidgetKit.scheduleClock(context);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetKit.scheduleClock(context);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetKit.cancelClock(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && WidgetKit.TICK.equals(intent.getAction())) {
            WidgetKit.updateClocks(context);
            return;
        }
        super.onReceive(context, intent);
    }
}
