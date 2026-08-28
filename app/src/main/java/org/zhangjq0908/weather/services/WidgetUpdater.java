package org.zhangjq0908.weather.services;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.zhangjq0908.weather.R;

import java.util.List;

public class WidgetUpdater extends Worker {
    private static final String TAG = "WidgetUpdater";

    public WidgetUpdater(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        if (isStopped()) return Result.failure();

        //watchdog: if background sync has stalled, force it before re-rendering
        WeatherSyncScheduler.checkWatchdog(getApplicationContext());

        // Runs as regular background WorkManager work. This task only re-renders
        // widgets from the database (no long-running network), so it does not
        // need a foreground service notification.
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
        List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();

        for (AppWidgetProviderInfo info : providers) {
            ComponentName provider = info.provider;
            if (provider.getPackageName().equals(getApplicationContext().getPackageName())) {
                int[] widgetIds = appWidgetManager.getAppWidgetIds(provider);
                Log.d(TAG, provider.getClassName() + widgetIds.length);
                Intent intent = new Intent();
                intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                intent.setComponent(provider); // this is the ComponentName
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
                getApplicationContext().sendBroadcast(intent);
            }
        }

        // Indicate whether the work finished successfully with the Result
        return Result.success();
    }

}
