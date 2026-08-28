package org.zhangjq0908.weather;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.osmdroid.config.Configuration;

public class WeatherApplication extends Application implements androidx.work.Configuration.Provider {

    @Override
    public void onCreate() {
        super.onCreate();
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
    }

    /**
     * On-demand WorkManager configuration. Whether the default
     * WorkManagerInitializer survives manifest merging or is removed,
     * WorkManager always initializes through this provider with our settings.
     */
    @NonNull
    @Override
    public androidx.work.Configuration getWorkManagerConfiguration() {
        return new androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build();
    }
}
