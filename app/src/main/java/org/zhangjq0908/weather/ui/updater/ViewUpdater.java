package org.zhangjq0908.weather.ui.updater;

import android.os.Handler;
import android.os.Looper;

import org.zhangjq0908.weather.database.CurrentWeatherData;
import org.zhangjq0908.weather.database.HourlyForecast;
import org.zhangjq0908.weather.database.WeekForecast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by chris on 24.01.2017.
 */

public class ViewUpdater {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static List<IUpdateableCityUI> subscribers = new ArrayList<>();

    public static void addSubscriber(IUpdateableCityUI sub) {
        if (!subscribers.contains(sub)) {
            subscribers.add(sub);
        }
    }

    public static void removeSubscriber(IUpdateableCityUI sub) {
        subscribers.remove(sub);
    }

    public static void updateCurrentWeatherData(final CurrentWeatherData data) {
        MAIN_HANDLER.post(() -> {
            ArrayList<IUpdateableCityUI> subcopy = new ArrayList<>(subscribers);  //copy list needed as bugfix for concurrent modification exception
            for (IUpdateableCityUI sub : subcopy) {
                sub.processNewCurrentWeatherData(data);
            }
        });
    }

    public static void updateWeekForecasts(final List<WeekForecast> forecasts) {
        MAIN_HANDLER.post(() -> {
            ArrayList<IUpdateableCityUI> subcopy = new ArrayList<>(subscribers);
            for (IUpdateableCityUI sub : subcopy) {
                sub.processNewWeekForecasts(forecasts);
            }
        });
    }

    public static void updateForecasts(final List<HourlyForecast> hourlyForecasts) {
        MAIN_HANDLER.post(() -> {
            ArrayList<IUpdateableCityUI> subcopy = new ArrayList<>(subscribers);
            for (IUpdateableCityUI sub : subcopy) {
                sub.processNewForecasts(hourlyForecasts);
            }
        });
    }
}
