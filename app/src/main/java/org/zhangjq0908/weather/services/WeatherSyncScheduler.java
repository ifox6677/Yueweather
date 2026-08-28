package org.zhangjq0908.weather.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.preference.PreferenceManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.zhangjq0908.weather.database.CityToWatch;
import org.zhangjq0908.weather.database.CurrentWeatherData;
import org.zhangjq0908.weather.database.SQLiteHelper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WorkManager — diagram top level.
 * <pre>
 *                WorkManager
 *                     │
 *        ┌────────────┴────────────┐
 *        │                         │
 *   PeriodicWork               WidgetWork
 *        │                         │
 *   正常唤醒                     Watchdog
 *        │                         │
 *        └──────────┬──────────────┘
 *                   ↓
 *          enqueueCity(cityId) → weatherSyncCity-{id} → KEEP → WeatherUpdateWorker
 * </pre>
 */
public final class WeatherSyncScheduler {
    public static final String PREF_SYNC_INTERVAL_MINUTES = "pref_sync_interval_minutes";
    /** PeriodicWork name — 正常唤醒 */
    private static final String PERIODIC_WORK_NAME = "weatherSyncPeriodic";
    private static final String ONE_TIME_WORK_NAME = "weatherSyncImmediate";
    /** WidgetWork name — Watchdog carrier (WidgetUpdater) */
    private static final String WIDGET_UPDATE_WORK_NAME = "widgetUpdateWork";
    private static final String PREF_WIDGET_UPDATE_SCHEDULED = "pref_widget_update_work_scheduled";
    private static final String PREF_SYNC_SCHEDULED = "pref_sync_scheduled";
    private static final String PREF_LAST_WATCHDOG = "pref_last_watchdog";
    private static final long WATCHDOG_COOLDOWN_MS = 30 * 60_000L;
    private static final long DEFAULT_PERIODIC_MINUTES = 30;

    private WeatherSyncScheduler() {
    }

    /**
     * PeriodicWork — 正常唤醒.
     * Registers a PeriodicWorkRequest for WeatherUpdateWorker (as dispatcher).
     * The worker itself fans out to per-city weatherSyncCity-{id} KEEP works.
     */
    public static void ensureScheduled(@NonNull Context context, boolean runImmediately) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        long intervalMinutes = getIntervalMinutes(context);
        //WorkManager requires 15min <= flex <= interval
        long flexMinutes = Math.min(intervalMinutes, Math.max(15, intervalMinutes / 2));
        PeriodicWorkRequest periodicRequest =
                new PeriodicWorkRequest.Builder(WeatherUpdateWorker.class, intervalMinutes, TimeUnit.MINUTES,
                        flexMinutes, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                        .build();

        //UPDATE refreshes the spec (e.g. changed interval) without resetting the periodic schedule
        workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
        );

        if (runImmediately) {
            OneTimeWorkRequest oneTimeRequest =
                    new OneTimeWorkRequest.Builder(WeatherUpdateWorker.class)
                            .setConstraints(constraints)
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                            .build();
            workManager.enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    oneTimeRequest
            );
        }

        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit()
                .putBoolean(PREF_SYNC_SCHEDULED, true)
                .apply();

        ensureWidgetUpdateScheduled(context);
    }

    /**
     * Cheap guard for code paths invoked on every widget refresh: skips the
     * periodic (re-)registration once scheduling has been set up, while
     * SettingsActivity keeps calling the full ensureScheduled() so interval
     * changes are applied immediately via the UPDATE policy.
     */
    public static void ensureScheduledGuarded(@NonNull Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getBoolean(PREF_SYNC_SCHEDULED, false)) {
            return;
        }
        ensureScheduled(context, false);
    }

    /**
     * WidgetWork — Watchdog carrier.
     * Registers the widget re-render periodic work exactly once per installation.
     * Every 20-min run executes WidgetUpdater → checkWatchdog() → enqueueCity,
     * so the two branches converge on the same weatherSyncCity-{id} KEEP work.
     */
    public static void ensureWidgetUpdateScheduled(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (PreferenceManager.getDefaultSharedPreferences(appContext)
                .getBoolean(PREF_WIDGET_UPDATE_SCHEDULED, false)) {
            return;
        }

        //no network/battery constraints: this only re-renders widgets from the
        //database and must keep running even at low battery or off-screen
        PeriodicWorkRequest widgetUpdateRequest =
                new PeriodicWorkRequest.Builder(WidgetUpdater.class,
                        20, TimeUnit.MINUTES, 15, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(appContext)
                .enqueueUniquePeriodicWork(WIDGET_UPDATE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP, widgetUpdateRequest);

        PreferenceManager.getDefaultSharedPreferences(appContext)
                .edit()
                .putBoolean(PREF_WIDGET_UPDATE_SCHEDULED, true)
                .apply();
    }

    /**
     * Watchdog — invoked from WidgetUpdater (WidgetWork) each 20-min run.
     * If no sync has succeeded within 2 sync periods, forces a full sync —
     * now using per-city lastSuccess so each city self-heals independently.
     * All enqueue paths converge on enqueueCity → weatherSyncCity-{id} → KEEP.
     */
    public static void checkWatchdog(Context context) {
        Context appContext = context.getApplicationContext();
        List<CityToWatch> cities = SQLiteHelper.getInstance(appContext).getAllCitiesToWatch();
        if (cities.isEmpty()) return;

        long thresholdMs = getIntervalMinutes(appContext) * 60_000L * 2;
        long now = System.currentTimeMillis();
        boolean anyStale = false;
        for (CityToWatch city : cities) {
            long last = WeatherUpdateWorker.getLastCitySuccess(appContext, city.getCityId());
            // fall back to per-city DB timestamp if no prefs yet (migration)
            if (last == 0) {
                CurrentWeatherData data = SQLiteHelper.getInstance(appContext).getCurrentWeatherByCityId(city.getCityId());
                if (data != null) last = data.getTimestamp() * 1000L;
            }
            if (last == 0 || now - last > thresholdMs) {
                anyStale = true;
                break;
            }
        }
        if (anyStale) {
            triggerWatchdogSync(appContext);
        }
    }

    /**
     * Cooldown-guarded forced full sync, so an offline/stuck period does not
     * re-enqueue work on every 20-min widget run.
     * Fan-out uses WeatherUpdateWorker.enqueueFullSync → per-city KEEP.
     */
    private static void triggerWatchdogSync(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long now = SystemClock.elapsedRealtime();
        long since = now - prefs.getLong(PREF_LAST_WATCHDOG, -WATCHDOG_COOLDOWN_MS);
        if (since >= 0 && since < WATCHDOG_COOLDOWN_MS) return;
        prefs.edit().putLong(PREF_LAST_WATCHDOG, now).apply();
        WeatherUpdateWorker.enqueueFullSync(context);
    }

    /**
     * Widget rendering path helper. Returns true when the
     * widget city's stored data is older than {@code multiplier} sync periods,
     * so a widget the user is actually looking at refreshes immediately.
     * Caller then chooses enqueueCityForce vs enqueueCity, both converging
     * on same weatherSyncCity-{id} KEEP work.
     */
    public static boolean isWidgetDataStale(Context context, double multiplier) {
        Context appContext = context.getApplicationContext();
        int cityId = SQLiteHelper.getWidgetCityID(appContext);
        CurrentWeatherData data = SQLiteHelper.getInstance(appContext).getCurrentWeatherByCityId(cityId);
        if (data == null) return true;
        long ageMs = System.currentTimeMillis() - data.getTimestamp() * 1000L;
        long thresholdMs = (long) (getIntervalMinutes(appContext) * 60_000.0 * multiplier);
        return ageMs > thresholdMs;
    }

    private static long getIntervalMinutes(@NonNull Context context) {
        String rawValue = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getString(PREF_SYNC_INTERVAL_MINUTES, String.valueOf(DEFAULT_PERIODIC_MINUTES));
        try {
            long minutes = Long.parseLong(rawValue);
            return Math.max(15, minutes);
        } catch (NumberFormatException e) {
            return DEFAULT_PERIODIC_MINUTES;
        }
    }
}
