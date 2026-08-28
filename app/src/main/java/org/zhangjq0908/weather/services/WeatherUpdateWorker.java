package org.zhangjq0908.weather.services;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.icu.util.LocaleData;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.zhangjq0908.weather.BuildConfig;
import org.zhangjq0908.weather.R;
import org.zhangjq0908.weather.activities.NavigationActivity;
import org.zhangjq0908.weather.database.CityToWatch;
import org.zhangjq0908.weather.database.CurrentWeatherData;
import org.zhangjq0908.weather.database.SQLiteHelper;
import org.zhangjq0908.weather.http.VolleySingleton;
import org.zhangjq0908.weather.ui.Help.StringFormatUtils;
import org.zhangjq0908.weather.weather_api.open_meteo.OMHttpRequestForWeatherAPI;
import org.zhangjq0908.weather.weather_api.open_meteo.ProcessOMweatherAPIRequest;
import org.zhangjq0908.weather.widget.RadarWidget;
import org.zhangjq0908.weather.widget.WeatherWidgetAllInOne;

import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetches forecast data (and radar tiles for radar widgets) synchronously in the
 * background. Replaces the former JobIntentService-based UpdateDataService.
 */
public class WeatherUpdateWorker extends Worker {

    public static final String KEY_CITY_ID = "cityId";
    private static final String TAG = "WeatherUpdateWorker";
    private static final String UNIQUE_WORK_NAME_PREFIX = "weatherUpdate-";
    private static final int REQUEST_TIMEOUT_SECONDS = 30;

    public WeatherUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueueCityUpdate(Context context, int cityId) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        Data input = new Data.Builder()
                .putInt(KEY_CITY_ID, cityId)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WeatherUpdateWorker.class)
                .setConstraints(constraints)
                .setInputData(input)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_WORK_NAME_PREFIX + cityId, ExistingWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        if (!isOnline(context, 2000)) {
            showToastIfVisible(context, context.getString(R.string.error_no_internet));
            return Result.retry();
        }

        int cityId = getInputData().getInt(KEY_CITY_ID, -1);
        SQLiteHelper db = SQLiteHelper.getInstance(context);
        List<CityToWatch> cities = db.getAllCitiesToWatch();
        if (cities.isEmpty()) return Result.success();

        boolean success = true;
        if (cityId >= 0) {
            CityToWatch target = null;
            for (CityToWatch city : cities) {
                if (city.getCityId() == cityId) {
                    target = city;
                    break;
                }
            }
            if (target == null) return Result.success();  //city was removed meanwhile
            success = updateWeatherData(context, target);
        } else {
            for (CityToWatch city : cities) {
                if (!updateWeatherData(context, city)) success = false;
            }
        }

        if (!success) {
            return getRunAttemptCount() < 2 ? Result.retry() : Result.failure();
        }
        return Result.success();
    }

    private boolean updateWeatherData(Context context, CityToWatch city) {
        try {
            OMHttpRequestForWeatherAPI api = new OMHttpRequestForWeatherAPI(context);
            String url = api.getUrlForQueryingOMweatherAPI(context, city.getLatitude(), city.getLongitude());

            RequestFuture<String> future = RequestFuture.newFuture();
            StringRequest request = new StringRequest(Request.Method.GET, url, future, future);
            VolleySingleton.get(context).add(request);
            String response = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            new ProcessOMweatherAPIRequest(context).processSuccessScenario(response, city.getCityId());

            updateRadarIfNeeded(context, city.getCityId());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Weather update interrupted for city " + city.getCityId(), e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Weather update failed for city " + city.getCityId(), e);
            showToastIfVisible(context, context.getString(R.string.error_fetch_forecast));
            return false;
        }
    }

    private void updateRadarIfNeeded(Context context, int cityId) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int numRadarWidgets = appWidgetManager.getAppWidgetIds(new ComponentName(context, RadarWidget.class)).length;
        int numAllInOneWidgets = appWidgetManager.getAppWidgetIds(new ComponentName(context, WeatherWidgetAllInOne.class)).length;
        if (numRadarWidgets + numAllInOneWidgets == 0) return;
        if (cityId != SQLiteHelper.getWidgetCityID(context)) return;

        SQLiteHelper db = SQLiteHelper.getInstance(context);
        CityToWatch city = db.getCityToWatch(cityId);

        try {
            RequestFuture<JSONObject> jsonFuture = RequestFuture.newFuture();
            JsonObjectRequest jsonRequest = new JsonObjectRequest(
                    Request.Method.GET, "https://api.rainviewer.com/public/weather-maps.json", null, jsonFuture, jsonFuture);
            jsonRequest.setRetryPolicy(new DefaultRetryPolicy(3000, 1, 1.0f));
            VolleySingleton.get(context).add(jsonRequest);
            JSONObject response = jsonFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            String host = response.getString("host");
            JSONArray pastFrames = response.getJSONObject("radar").getJSONArray("past");
            if (pastFrames.length() == 0) return;
            JSONObject lastFrame = pastFrames.getJSONObject(pastFrames.length() - 1);
            String path = lastFrame.getString("path");
            long radarTimeGMT = lastFrame.getLong("time") * 1000L;
            int zoom = 7;
            String radarUrl = host + path + "/256/" + zoom + "/" + city.getLatitude() + "/" + city.getLongitude() + "/2/1_1.png";

            RequestFuture<Bitmap> imageFuture = RequestFuture.newFuture();
            ImageRequest imageRequest = new ImageRequest(radarUrl, imageFuture,
                    0, 0, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565, imageFuture);
            imageRequest.setRetryPolicy(new DefaultRetryPolicy(3000, 1, 1.0f));
            VolleySingleton.get(context).add(imageRequest);
            Bitmap radarBitmap = imageFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            RadarWidget.radarBitmap = radarBitmap;
            WeatherWidgetAllInOne.radarBitmap = radarBitmap;
            RadarWidget.radarTimeGMT = radarTimeGMT;
            WeatherWidgetAllInOne.radarTimeGMT = radarTimeGMT;
            RadarWidget.radarZoom = zoom;
            WeatherWidgetAllInOne.radarZoom = zoom;

            CurrentWeatherData currentWeatherData = db.getCurrentWeatherByCityId(cityId);
            int zoneseconds = currentWeatherData != null ? currentWeatherData.getTimeZoneSeconds() : 0;

            int[] widgetIDs = appWidgetManager.getAppWidgetIds(new ComponentName(context, RadarWidget.class));
            if (widgetIDs.length > 0) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.radar_widget);
                views.setImageViewBitmap(R.id.widget_radar_view,
                        prepareRadarWidget(context, city, zoom, radarTimeGMT + zoneseconds * 1000L, radarBitmap));
                appWidgetManager.partiallyUpdateAppWidget(widgetIDs, views);
            }

            widgetIDs = appWidgetManager.getAppWidgetIds(new ComponentName(context, WeatherWidgetAllInOne.class));
            if (widgetIDs.length > 0) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget_all_in_one);
                views.setImageViewBitmap(R.id.widget_radar_view,
                        prepareAllInOneWidget(context, city, zoom, radarTimeGMT + zoneseconds * 1000L, radarBitmap));
                appWidgetManager.partiallyUpdateAppWidget(widgetIDs, views);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Radar update interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "Radar update failed", e);  //radar failure must not fail the weather job
        }
    }

    private boolean isOnline(Context context, int timeOutMs) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<InetAddress> future = executor.submit(() -> {
                try {
                    URL url = new URL(BuildConfig.BASE_URL);
                    return InetAddress.getByName(url.getHost());
                } catch (java.io.IOException e) {
                    return null;
                }
            });
            InetAddress inetAddress = future.get(timeOutMs, TimeUnit.MILLISECONDS);
            return inetAddress != null && !inetAddress.toString().isEmpty();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void showToastIfVisible(final Context context, final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (NavigationActivity.isVisible) Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        });
    }

    @NonNull
    public static Bitmap prepareAllInOneWidget(Context context, CityToWatch city, int zoom, long radarTime, Bitmap response1) {
        Bitmap textBitmap = Bitmap.createBitmap(response1.getWidth(), response1.getHeight(), response1.getConfig());
        Canvas canvas = new Canvas(textBitmap);
        canvas.drawBitmap(response1, 0, 0, null); // draw the original image

        Paint paint = new Paint();
        paint.setColor(androidx.core.content.ContextCompat.getColor(context, R.color.lightgrey));
        paint.setTextSize(30);
        paint.setStrokeWidth(3.0f);

        int widthTotalDistance = (int) (2 * 3.14 * 6378 * Math.abs(Math.cos(city.getLatitude() / 180 * 3.14)) / (Math.pow(2, zoom) * 256) * 256); ;
        String distanceUnit = context.getString(R.string.units_km);;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (LocaleData.getMeasurementSystem(ULocale.forLocale(Locale.getDefault())) != LocaleData.MeasurementSystem.SI){
                distanceUnit = context.getString(R.string.units_mi);
                widthTotalDistance = (int) (2 * 3.14 * 6378 * 0.6214 * Math.abs(Math.cos(city.getLatitude() / 180 * 3.14)) / (Math.pow(2, zoom) * 256) * 256);
            }
        }

        int widthDistanceMarker = getClosestMarker(widthTotalDistance / 10);
        int widthDistanceMarkerPixel = widthDistanceMarker * 256 / widthTotalDistance;

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(widthDistanceMarker + " " + distanceUnit, 7 + widthDistanceMarkerPixel + 5, 238 + 8, paint); // draw the text

        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(StringFormatUtils.formatTimeWithoutZone(context, radarTime), 248, 238 + 8, paint);

        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(7, 238, 7 + widthDistanceMarkerPixel, 238, paint);

        int maxI = 100 / widthDistanceMarkerPixel;
        for (int i = 1; i <= maxI; i++) {
            int radius = i * widthDistanceMarkerPixel;
            canvas.drawCircle(128, 128, radius, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(128, 128, 2, paint);

        //Round off corners
        Paint clearPaint = new Paint();
        clearPaint.setStyle(Paint.Style.STROKE);
        clearPaint.setStrokeWidth(20.0f);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRoundRect(-10, -10,265, 265, 30, 30, clearPaint);
        return textBitmap;
    }

    @NonNull
    public static Bitmap prepareRadarWidget(Context context, CityToWatch city, int zoom, long radarTime, Bitmap response1) {
        Bitmap textBitmap = Bitmap.createBitmap(response1.getWidth(), response1.getHeight(), response1.getConfig());
        Canvas canvas = new Canvas(textBitmap);
        canvas.drawBitmap(response1, 0, 0, null); // draw the original image
        Paint paint = new Paint();
        paint.setColor(androidx.core.content.ContextCompat.getColor(context, R.color.lightgrey));
        paint.setTextSize(16);

        int widthTotalDistance = (int) (2 * 3.14 * 6378 * Math.abs(Math.cos(city.getLatitude() / 180 * 3.14)) / (Math.pow(2, zoom) * 256) * 256);

        String distanceUnit = context.getString(R.string.units_km);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (LocaleData.getMeasurementSystem(ULocale.forLocale(Locale.getDefault())) != LocaleData.MeasurementSystem.SI){
                distanceUnit = context.getString(R.string.units_mi);
                widthTotalDistance = (int) (2 * 3.14 * 6378 * 0.6214 * Math.abs(Math.cos(city.getLatitude() / 180 * 3.14)) / (Math.pow(2, zoom) * 256) * 256);
            }
        }

        int widthDistanceMarker = getClosestMarker(widthTotalDistance / 10);
        int widthDistanceMarkerPixel = widthDistanceMarker * 256 / widthTotalDistance;

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(widthDistanceMarker + " " + distanceUnit, 10 + widthDistanceMarkerPixel + 10, 240 + 5, paint); // draw the text

        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(StringFormatUtils.formatTimeWithoutZone(context, radarTime), 240, 240 + 5, paint);

        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(10, 240, 10 + widthDistanceMarkerPixel, 240, paint);

        int maxI = 100 / widthDistanceMarkerPixel;
        for (int i = 1; i <= maxI; i++) {
            int radius = i * widthDistanceMarkerPixel;
            canvas.drawCircle(128, 128, radius, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(128, 128, 2, paint);

        //Round off corners
        Paint clearPaint = new Paint();
        clearPaint.setStyle(Paint.Style.STROKE);
        clearPaint.setStrokeWidth(20.0f);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRoundRect(-10, -10,265, 265, 30, 30, clearPaint);
        return textBitmap;
    }

    private static int getClosestMarker(int value) {
        int[] markers = {1, 2, 3, 5, 10, 20, 30, 50, 100};
        int closest = markers[0];
        int minDiff = Math.abs(value - closest);
        for (int i = 1; i < markers.length; i++) {
            int diff = Math.abs(value - markers[i]);
            if (diff < minDiff) {
                minDiff = diff;
                closest = markers[i];
            }
        }
        return closest;
    }
}
