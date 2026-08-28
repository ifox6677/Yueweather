package org.zhangjq0908.weather.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.zhangjq0908.weather.firststart.TutorialActivity;
import org.zhangjq0908.weather.preferences.AppPreferencesManager;
import org.zhangjq0908.weather.services.WeatherSyncScheduler;

/**
 * Created by yonjuni on 24.10.16.
 */

public class SplashActivity extends AppCompatActivity {
    private static final String PREF_BATTERY_PROMPT_COUNT = "battery_optimization_prompt_count";
    private static final int MAX_BATTERY_PROMPTS = 3;

    private AppPreferencesManager prefManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WeatherSyncScheduler.ensureScheduled(this, true);

        prefManager = new AppPreferencesManager(PreferenceManager.getDefaultSharedPreferences(this));
        if (prefManager.isFirstTimeLaunch(this)){  //First time got to TutorialActivity
            Intent mainIntent = new Intent(SplashActivity.this, TutorialActivity.class);
            SplashActivity.this.startActivity(mainIntent);
        } else { //otherwise directly start ForecastCityActivity
            Intent mainIntent = new Intent(SplashActivity.this, ForecastCityActivity.class);
            SplashActivity.this.startActivity(mainIntent);
        }
        maybeRequestIgnoreBatteryOptimizations();
        SplashActivity.this.finish();
    }

    /**
     * Periodic WorkManager updates are delayed or dropped while the app is in
     * Doze on aggressive OEM builds. Ask the user up to MAX_BATTERY_PROMPTS
     * times (counter resets whenever a new widget is added) to exempt the app.
     */
    private void maybeRequestIgnoreBatteryOptimizations() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null || powerManager.isIgnoringBatteryOptimizations(getPackageName())) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int promptCount = prefs.getInt(PREF_BATTERY_PROMPT_COUNT, 0);
        if (promptCount >= MAX_BATTERY_PROMPTS) return;
        prefs.edit().putInt(PREF_BATTERY_PROMPT_COUNT, promptCount + 1).apply();

        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            //device has no handler for this settings action
        }
    }

}
