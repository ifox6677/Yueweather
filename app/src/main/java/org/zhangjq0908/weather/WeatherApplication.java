package org.zhangjq0908.weather;

import android.app.Application;

import androidx.preference.PreferenceManager;

import org.osmdroid.config.Configuration;

public class WeatherApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
    }
}
