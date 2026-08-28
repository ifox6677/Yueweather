package org.zhangjq0908.weather.widget;


import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.zhangjq0908.weather.R;
import org.zhangjq0908.weather.activities.ForecastCityActivity;
import org.zhangjq0908.weather.database.CityToWatch;
import org.zhangjq0908.weather.database.CurrentWeatherData;
import org.zhangjq0908.weather.database.SQLiteHelper;
import org.zhangjq0908.weather.database.WeekForecast;
import org.zhangjq0908.weather.services.WeatherSyncScheduler;
import org.zhangjq0908.weather.services.WeatherUpdateWorker;
import org.zhangjq0908.weather.ui.Help.StringFormatUtils;
import org.zhangjq0908.weather.ui.UiResourceProvider;
import static org.zhangjq0908.weather.database.SQLiteHelper.getWidgetCityID;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import androidx.preference.PreferenceManager;

public class WeatherWidget5day extends AppWidgetProvider {

    public void updateAppWidget(Context context, final int appWidgetId) {

        SQLiteHelper db = SQLiteHelper.getInstance(context);
        if (!db.getAllCitiesToWatch().isEmpty()) {

            int cityID = getWidgetCityID(context);

            WeatherSyncScheduler.ensureScheduledGuarded(context.getApplicationContext());
            if (WeatherSyncScheduler.isWidgetDataStale(context, 1.5)) {
                WeatherUpdateWorker.enqueueCityUpdateForce(context, cityID);
            } else {
                WeatherUpdateWorker.enqueueCityUpdate(context, cityID);
            }
        }
    }


    public static void updateView(Context context, AppWidgetManager appWidgetManager, RemoteViews views, int appWidgetId, CityToWatch city, List<WeekForecast> weekforecasts) {
        if (weekforecasts == null || weekforecasts.isEmpty()) return;  //no forecast data - nothing to render

        SharedPreferences prefManager = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        views.setInt(R.id.widget_background,"setAlpha",  (int) ((100.0f - prefManager.getInt("pref_WidgetTransparency", 0)) * 255 / 100.0f));
        int cityId=getWidgetCityID(context);
        SQLiteHelper database = SQLiteHelper.getInstance(context.getApplicationContext());
        CurrentWeatherData currentWeather = database.getCurrentWeatherByCityId(cityId);
        if (currentWeather == null) return;  //data not ready yet - nothing to render
        int zonemilliseconds = currentWeather.getTimeZoneSeconds()*1000;
        float latitude = database.getCityToWatch(cityId) != null ? database.getCityToWatch(cityId).getLatitude() : 0f;

        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("GMT"));

        int n = Math.min(5, weekforecasts.size());
        int []forecastData = new int[5];
        boolean[] isDay = new boolean[5];
        String []weekday = new String[5];
        for (int i=0;i<n;i++){
            c.setTimeInMillis(weekforecasts.get(i).getForecastTime()+zonemilliseconds);

            if ((currentWeather.getTimeSunrise() - currentWeather.getTimeSunset()) % 86400 == 0) {
                if (latitude > 0) {  //northern hemisphere
                    isDay[i] = c.get(Calendar.DAY_OF_YEAR) >= 80 && c.get(Calendar.DAY_OF_YEAR) <= 265;  //from March 21 to September 22 (incl)
                } else { //southern hemisphere
                    isDay[i] = c.get(Calendar.DAY_OF_YEAR) < 80 || c.get(Calendar.DAY_OF_YEAR) > 265;
                }
            } else {
                //real sunrise/sunset available - judge day/night from that day's window
                Calendar sunRise = Calendar.getInstance();
                sunRise.setTimeZone(TimeZone.getTimeZone("GMT"));
                sunRise.setTimeInMillis(currentWeather.getTimeSunrise() * 1000 + zonemilliseconds);
                sunRise.set(Calendar.DAY_OF_YEAR, c.get(Calendar.DAY_OF_YEAR));
                sunRise.set(Calendar.YEAR, c.get(Calendar.YEAR));
                Calendar sunSet = Calendar.getInstance();
                sunSet.setTimeZone(TimeZone.getTimeZone("GMT"));
                sunSet.setTimeInMillis(currentWeather.getTimeSunset() * 1000 + zonemilliseconds);
                sunSet.set(Calendar.DAY_OF_YEAR, c.get(Calendar.DAY_OF_YEAR));
                sunSet.set(Calendar.YEAR, c.get(Calendar.YEAR));
                isDay[i] = c.after(sunRise) && c.before(sunSet);
            }

            int day = c.get(Calendar.DAY_OF_WEEK);
            weekday[i]=context.getResources().getString(StringFormatUtils.getDayShort(day));

            forecastData[i]=weekforecasts.get(i).getWeatherID();

        }

        int[] imageIds = {R.id.widget_5day_image1, R.id.widget_5day_image2, R.id.widget_5day_image3, R.id.widget_5day_image4, R.id.widget_5day_image5};
        int[] windIds  = {R.id.widget_5day_wind1, R.id.widget_5day_wind2, R.id.widget_5day_wind3, R.id.widget_5day_wind4, R.id.widget_5day_wind5};
        int[] dayIds   = {R.id.widget_5day_day1, R.id.widget_5day_day2, R.id.widget_5day_day3, R.id.widget_5day_day4, R.id.widget_5day_day5};
        int[] tempMaxIds = {R.id.widget_5day_temp_max1, R.id.widget_5day_temp_max2, R.id.widget_5day_temp_max3, R.id.widget_5day_temp_max4, R.id.widget_5day_temp_max5};
        int[] tempMinIds = {R.id.widget_5day_temp_min1, R.id.widget_5day_temp_min2, R.id.widget_5day_temp_min3, R.id.widget_5day_temp_min4, R.id.widget_5day_temp_min5};

        for (int j=0;j<5;j++){
            if (j < n) {
                views.setImageViewResource(imageIds[j], UiResourceProvider.getIconResourceForWeatherCategory(forecastData[j], isDay[j]));
                views.setTextViewText(dayIds[j], weekday[j]);
                views.setTextViewText(tempMaxIds[j], StringFormatUtils.formatTemperature(context, weekforecasts.get(j).getMaxTemperature()));
                views.setTextViewText(tempMinIds[j], StringFormatUtils.formatTemperature(context, weekforecasts.get(j).getMinTemperature()));
                views.setImageViewResource(windIds[j], StringFormatUtils.colorWindSpeedWidget(weekforecasts.get(j).getWind_speed()));
                for (int r = 0; r < 5; r++) views.setViewVisibility(dayRowId(j, r), View.VISIBLE);
            } else {
                for (int r = 0; r < 5; r++) views.setViewVisibility(dayRowId(j, r), View.INVISIBLE);
            }
        }

        Intent intent2 = new Intent(context, ForecastCityActivity.class);
        intent2.putExtra("cityId", getWidgetCityID(context));
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent2, PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent2, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        views.setOnClickPendingIntent(R.id.widget5day_layout, pendingIntent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static int dayRowId(int dayIndex, int element) {
        int[][] rows = {
                {R.id.widget_5day_day1, R.id.widget_5day_image1, R.id.widget_5day_wind1, R.id.widget_5day_temp_max1, R.id.widget_5day_temp_min1},
                {R.id.widget_5day_day2, R.id.widget_5day_image2, R.id.widget_5day_wind2, R.id.widget_5day_temp_max2, R.id.widget_5day_temp_min2},
                {R.id.widget_5day_day3, R.id.widget_5day_image3, R.id.widget_5day_wind3, R.id.widget_5day_temp_max3, R.id.widget_5day_temp_min3},
                {R.id.widget_5day_day4, R.id.widget_5day_image4, R.id.widget_5day_wind4, R.id.widget_5day_temp_max4, R.id.widget_5day_temp_min4},
                {R.id.widget_5day_day5, R.id.widget_5day_image5, R.id.widget_5day_wind5, R.id.widget_5day_temp_max5, R.id.widget_5day_temp_min5}
        };
        return rows[dayIndex][element];
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {

    }

    @Override
    public void onEnabled(Context context) {
        SharedPreferences prefManager = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        prefManager.edit().remove("battery_optimization_prompt_count").apply();
        // Enter relevant functionality for when the first widget is created
        SQLiteHelper dbHelper = SQLiteHelper.getInstance(context);

        int widgetCityID=getWidgetCityID(context);

        List<WeekForecast> weekforecasts=dbHelper.getWeekForecastsByCityId(widgetCityID);

        int[] widgetIDs = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, WeatherWidget5day.class));

        for (int widgetID : widgetIDs) {

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget_5day);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

            CityToWatch city=dbHelper.getCityToWatch(widgetCityID);

            WeatherWidget5day.updateView(context, appWidgetManager, views, widgetID, city, weekforecasts);

        }
     }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }

}

