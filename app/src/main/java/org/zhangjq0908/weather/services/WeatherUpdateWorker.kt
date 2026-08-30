package org.zhangjq0908.weather.services

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.*
import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.ImageRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.zhangjq0908.weather.R
import org.zhangjq0908.weather.activities.NavigationActivity
import org.zhangjq0908.weather.database.CityToWatch
import org.zhangjq0908.weather.database.SQLiteHelper
import org.zhangjq0908.weather.http.VolleySingleton
import org.zhangjq0908.weather.ui.Help.StringFormatUtils
import org.zhangjq0908.weather.weather_api.open_meteo.OMHttpRequestForWeatherAPI
import org.zhangjq0908.weather.weather_api.open_meteo.ProcessOMweatherAPIRequest
import org.zhangjq0908.weather.widget.RadarStore
import org.zhangjq0908.weather.widget.RadarWidget
import org.zhangjq0908.weather.widget.WeatherWidgetAllInOne
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Fetches forecast data (and radar tiles for radar widgets) in the background.
 *
 * Written as a [CoroutineWorker]. Background network, no foreground service
 * notification is shown: on API 31+ FGS promotion can be rejected anyway, so
 * this simply runs as normal WorkManager work protected by its constraints and
 * retried with backoff if the process is killed later.
 *
 * Architecture (see diagram):
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
 *          enqueueCity(cityId)
 *                   ↓
 *        weatherSyncCity-{id}
 *                   ↓
 *                  KEEP
 *                   ↓
 *           WeatherUpdateWorker
 *                   ↓
 *             网络请求
 *             /      \
 *           成功      临时失败
 *            │          │
 *            ↓          ↓
 *      lastSuccess    retry
 *      city级记录
 *            │
 *            ↓
 *         Widget更新
 * </pre>
 * Both normal periodic wakeup and Watchdog converge on the single
 * [enqueueCity] entry with unique name {@code weatherSyncCity-{id}} and
 * [ExistingWorkPolicy.KEEP] dedup.
 */
class WeatherUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /* ────────────────────────── enqueueCity — diagram central node ────────────────────────── */

    /**
     * Diagram central node: enqueueCity(cityId) → weatherSyncCity-{id} → KEEP → Worker.
     * De-bounced (10s) entry for UI / widget onUpdate callers.
     * Periodic / Watchdog fan-out uses the same KEEP name via [enqueueCityInternal].
     */
    override suspend fun doWork(): Result {
        //cap retries so a permanently unreachable API cannot retry forever
        if (runAttemptCount >= MAX_RUN_ATTEMPTS) {
            Log.e(TAG, "Giving up after $runAttemptCount attempts")
            return Result.failure()
        }
        if (isStopped) return Result.failure()

        val context = applicationContext
        val db = SQLiteHelper.getInstance(context)
        val cities = db.allCitiesToWatch
        if (cities.isEmpty()) return Result.success()

        val cityId = inputData.getInt(KEY_CITY_ID, -1)
        if (cityId < 0) {
            // PeriodicWork 正常唤醒: fan-out to per-city KEEP works (diagram)
            enqueueFullSync(context)
            return Result.success()
        }

        val target = cities.firstOrNull { it.cityId == cityId } ?: return Result.success()
        return updateWeatherData(context, target)
    }

    /**
     * Diagram lower half:
     * 网络请求 → 成功 → lastSuccess(city级记录) → Widget更新
     *        ↘ 临时失败 → retry (WorkManager backoff)
     */
    private suspend fun updateWeatherData(context: Context, city: CityToWatch): Result {
        // Offload blocking Volley calls to the IO dispatcher instead of holding
        // a WorkManager worker thread while each request completes.
        return try {
            withContext(Dispatchers.IO) {
                val api = OMHttpRequestForWeatherAPI(context)
                val url = api.getUrlForQueryingOMweatherAPI(context, city.latitude, city.longitude)

                val response = await { success, error ->
                    StringRequest(Request.Method.GET, url, success, error).apply {
                        retryPolicy = DefaultRetryPolicy(10_000, 2, 1.0f)
                    }
                }

                // success path — DB + widget update inside processSuccessScenario
                ProcessOMweatherAPIRequest(context).processSuccessScenario(response, city.cityId)

                // city级记录 — diagram lastSuccess
                recordCitySuccess(context, city.cityId)

                // radar + widget partial update (city级 widget refresh is also done
                // inside ProcessOMweatherAPIRequest.possiblyUpdateWidgets, this
                // handles radar overlay for Radar/AllInOne widgets)
                updateRadarIfNeeded(context, city.cityId)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Weather update failed for city ${city.cityId}", e)
            showToastIfVisible(context, context.getString(R.string.error_fetch_forecast))
            Result.retry()
        }
    }

    private suspend fun updateRadarIfNeeded(context: Context, cityId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val numRadarWidgets =
            appWidgetManager.getAppWidgetIds(ComponentName(context, RadarWidget::class.java)).size
        val numAllInOneWidgets =
            appWidgetManager.getAppWidgetIds(ComponentName(context, WeatherWidgetAllInOne::class.java)).size
        if (numRadarWidgets + numAllInOneWidgets == 0) {
            RadarStore.clear(context)
            return
        }
        if (cityId != SQLiteHelper.getWidgetCityID(context)) return

        val db = SQLiteHelper.getInstance(context)
        val city = db.getCityToWatch(cityId) ?: return

        try {
            val response = await { success, error ->
                JsonObjectRequest(
                    Request.Method.GET, "https://api.rainviewer.com/public/weather-maps.json", null, success, error
                ).apply {
                    retryPolicy = DefaultRetryPolicy(3000, 1, 1.0f)
                }
            }

            val host = response.getString("host")
            val pastFrames = response.getJSONObject("radar").getJSONArray("past")
            if (pastFrames.length() == 0) return
            val lastFrame = pastFrames.getJSONObject(pastFrames.length() - 1)
            val path = lastFrame.getString("path")
            val radarTimeGMT = lastFrame.getLong("time") * 1000L
            val zoom = 7
            val radarUrl = host + path + "/256/" + zoom + "/" + city.latitude + "/" + city.longitude + "/2/1_1.png"

            val radarBitmap = await { success, error ->
                ImageRequest(
                    radarUrl, success,
                    0, 0, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565, error
                ).apply {
                    retryPolicy = DefaultRetryPolicy(3000, 1, 1.0f)
                }
            }

            //persist to cache instead of holding the bitmap in process memory
            RadarStore.save(context, radarBitmap, radarTimeGMT, zoom)

            val currentWeatherData = db.getCurrentWeatherByCityId(cityId)
            val zoneseconds = currentWeatherData?.timeZoneSeconds ?: 0

            var widgetIDs = appWidgetManager.getAppWidgetIds(ComponentName(context, RadarWidget::class.java))
            if (widgetIDs.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.radar_widget)
                views.setImageViewBitmap(
                    R.id.widget_radar_view,
                    prepareRadarWidget(context, city, zoom, radarTimeGMT + zoneseconds * 1000L, radarBitmap)
                )
                appWidgetManager.partiallyUpdateAppWidget(widgetIDs, views)
            }

            widgetIDs = appWidgetManager.getAppWidgetIds(ComponentName(context, WeatherWidgetAllInOne::class.java))
            if (widgetIDs.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.weather_widget_all_in_one)
                views.setImageViewBitmap(
                    R.id.widget_radar_view,
                    prepareAllInOneWidget(context, city, zoom, radarTimeGMT + zoneseconds * 1000L, radarBitmap)
                )
                appWidgetManager.partiallyUpdateAppWidget(widgetIDs, views)
            }
        } catch (e: CancellationException) {
            //worker stopped - propagate so the work is cancelled, not retried
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Radar update failed", e)  //radar failure must not fail the weather job
        }
    }

    private fun showToastIfVisible(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            if (NavigationActivity.isVisible) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Suspends until the Volley request built by [build] completes, resuming
     * with the parsed response or the Volley error. Unlike the old
     * RequestFuture 1s polling loop this holds no worker thread while waiting;
     * on timeout or worker stop the coroutine is cancelled and the underlying
     * Volley request is cancelled too, so no bandwidth is wasted.
     */
    private suspend fun <T> await(build: (Response.Listener<T>, Response.ErrorListener) -> Request<T>): T {
        val deferred = CompletableDeferred<T>()
        val request = build(
            Response.Listener { deferred.complete(it) },
            Response.ErrorListener { deferred.completeExceptionally(it) }
        )
        //cancel the underlying Volley request when the coroutine (and thus the
        //worker) is stopped/cancelled, so no bandwidth is wasted on a dropped response
        coroutineContext[Job]?.invokeOnCompletion { request.cancel() }
        try {
            return withTimeout(REQUEST_TIMEOUT_SECONDS * 1000L) {
                VolleySingleton.get(applicationContext).add(request)
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            //cancel the request and convert to a normal exception so
            //updateWeatherData retries instead of treating it as a worker stop
            request.cancel()
            throw TimeoutException("request timed out after $REQUEST_TIMEOUT_SECONDS s")
        }
    }

    companion object {
        const val KEY_CITY_ID = "cityId"
        /** Global last-success (legacy, kept for watchdog fallback). */
        const val PREF_LAST_SYNC_SUCCESS = "pref_last_sync_success"
        /** City-level last-success prefix: pref_last_sync_success_city_{id} */
        const val PREF_LAST_SYNC_SUCCESS_CITY_PREFIX = "pref_last_sync_success_city_"
        private const val TAG = "WeatherUpdateWorker"
        /** Unified unique work name prefix — diagram: weatherSyncCity-{id} */
        private const val UNIQUE_WORK_NAME_PREFIX = "weatherSyncCity-"
        private const val REQUEST_TIMEOUT_SECONDS = 30
        private const val ENQUEUE_DEBOUNCE_MS = 10_000L
        private const val FORCED_DEBOUNCE_MS = 30 * 60_000L
        private const val MAX_RUN_ATTEMPTS = 5

        @JvmStatic
        @JvmName("enqueueCity")
        fun enqueueCity(context: Context, cityId: Int) {
            enqueueCityUpdate(context, cityId)
        }

        /**
         * Fan-out target of the full sync (periodic + watchdog). Uses
         * its own unique-name namespace with KEEP so background scheduling neither
         * cancels user-triggered refreshes nor needs blocking WorkManager queries
         * to dedupe.
         */
        @JvmStatic
        @JvmName("enqueueCityUpdate")
        fun enqueueCityUpdate(context: Context, cityId: Int) {
            if (!allowEnqueue(context, cityId, ENQUEUE_DEBOUNCE_MS, "pref_last_city_enqueue_")) return
            enqueueCityInternal(context, cityId)
        }

        /**
         * Watchdog/rendering path: same as enqueueCityUpdate but with a much longer
         * cooldown, so a stale widget can force an immediate sync without being
         * swallowed by the 10s burst debounce and without hammering while offline.
         * Still converges on the same weatherSyncCity-{id} KEEP work.
         */
        @JvmStatic
        @JvmName("enqueueCityUpdateForce")
        fun enqueueCityUpdateForce(context: Context, cityId: Int) {
            if (!allowEnqueue(context, cityId, FORCED_DEBOUNCE_MS, "pref_last_forced_enqueue_")) return
            enqueueCityInternal(context, cityId, expedited = true)
        }

        private fun allowEnqueue(context: Context, cityId: Int, debounceMs: Long, keyPrefix: String): Boolean {
            val appContext = context.applicationContext
            val key = keyPrefix + cityId
            val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
            val now = SystemClock.elapsedRealtime()
            val since = now - prefs.getLong(key, -debounceMs)
            //since < 0 means the device rebooted (elapsedRealtime reset) - never suppress
            if (since >= 0 && since < debounceMs) return false
            prefs.edit().putLong(key, now).apply()
            return true
        }

        /**
         * Single KEEP-guarded enqueuer that both PeriodicWork (正常唤醒) and
         * WidgetWork/Watchdog converge on. Unique name weatherSyncCity-{id}.
         */
        private fun enqueueCityInternal(context: Context, cityId: Int, expedited: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val input = Data.Builder()
                .putInt(KEY_CITY_ID, cityId)
                .build()
            val builder = OneTimeWorkRequest.Builder(WeatherUpdateWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .setInputData(input)
            if (expedited) {
                //user-triggered / watchdog force refresh - run ASAP, falling back
                //to a regular queued work if the expedited quota is exhausted
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME_PREFIX + cityId, ExistingWorkPolicy.KEEP, builder.build())
        }

        /**
         * Fan-out target of the full sync (periodic + startup one-time + watchdog).
         * Each city converges on the same weatherSyncCity-{id} KEEP work.
         * Bypass debounce so a scheduled periodic run is never suppressed by the
         * 10s UI burst guard.
         */
        @JvmStatic
        @JvmName("enqueueFullSync")
        fun enqueueFullSync(context: Context) {
            val appContext = context.applicationContext
            val cities = SQLiteHelper.getInstance(appContext).allCitiesToWatch
            for (city in cities) {
                enqueueCityInternal(appContext, city.cityId)
            }
        }

        @JvmStatic
        @JvmName("recordCitySuccess")
        fun recordCitySuccess(context: Context, cityId: Int) {
            val now = System.currentTimeMillis()
            val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            prefs.edit()
                .putLong(PREF_LAST_SYNC_SUCCESS, now) // global fallback for watchdog
                .putLong(citySuccessKey(cityId), now) // city级记录 — diagram
                .apply()
        }

        @JvmStatic
        @JvmName("getLastCitySuccess")
        fun getLastCitySuccess(context: Context, cityId: Int): Long {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            val cityTs = prefs.getLong(citySuccessKey(cityId), 0)
            if (cityTs != 0L) return cityTs
            return prefs.getLong(PREF_LAST_SYNC_SUCCESS, 0) // fallback
        }

        @JvmStatic
        @JvmName("citySuccessKey")
        fun citySuccessKey(cityId: Int): String = PREF_LAST_SYNC_SUCCESS_CITY_PREFIX + cityId

        @JvmStatic
        @JvmName("prepareAllInOneWidget")
        fun prepareAllInOneWidget(
            context: Context,
            city: CityToWatch,
            zoom: Int,
            radarTime: Long,
            response1: Bitmap
        ): Bitmap {
            val textBitmap = Bitmap.createBitmap(response1.width, response1.height, response1.config!!)
            val canvas = Canvas(textBitmap)
            canvas.drawBitmap(response1, 0f, 0f, null) // draw the original image

            val paint = Paint()
            paint.color = ContextCompat.getColor(context, R.color.lightgrey)
            paint.textSize = 30f
            paint.strokeWidth = 3.0f

            var widthTotalDistance = (2 * 3.14 * 6378 * Math.abs(Math.cos(city.latitude / 180 * 3.14)).toDouble() /
                (Math.pow(2.0, zoom.toDouble()) * 256) * 256).toInt()
            var distanceUnit = context.getString(R.string.units_km)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (LocaleData.getMeasurementSystem(ULocale.forLocale(Locale.getDefault())) != LocaleData.MeasurementSystem.SI) {
                    distanceUnit = context.getString(R.string.units_mi)
                    widthTotalDistance = (2 * 3.14 * 6378 * 0.6214 * Math.abs(Math.cos(city.latitude / 180 * 3.14)).toDouble() /
                        (Math.pow(2.0, zoom.toDouble()) * 256) * 256).toInt()
                }
            }

            val widthDistanceMarker = getClosestMarker(widthTotalDistance / 10)
            val widthDistanceMarkerPixel = widthDistanceMarker * 256 / widthTotalDistance

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$widthDistanceMarker $distanceUnit", (7 + widthDistanceMarkerPixel + 5).toFloat(), (238 + 8).toFloat(), paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(StringFormatUtils.formatTimeWithoutZone(context, radarTime), 248f, (238 + 8).toFloat(), paint)

            paint.style = Paint.Style.STROKE
            canvas.drawLine(7f, 238f, (7 + widthDistanceMarkerPixel).toFloat(), 238f, paint)

            val maxI = 100 / widthDistanceMarkerPixel
            for (i in 1..maxI) {
                val radius = i * widthDistanceMarkerPixel
                canvas.drawCircle(128f, 128f, radius.toFloat(), paint)
            }

            paint.style = Paint.Style.FILL
            canvas.drawCircle(128f, 128f, 2f, paint)

            //Round off corners
            val clearPaint = Paint()
            clearPaint.style = Paint.Style.STROKE
            clearPaint.strokeWidth = 20.0f
            clearPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawRoundRect(-10f, -10f, 265f, 265f, 30f, 30f, clearPaint)
            return textBitmap
        }

        @JvmStatic
        @JvmName("prepareRadarWidget")
        fun prepareRadarWidget(
            context: Context,
            city: CityToWatch,
            zoom: Int,
            radarTime: Long,
            response1: Bitmap
        ): Bitmap {
            val textBitmap = Bitmap.createBitmap(response1.width, response1.height, response1.config!!)
            val canvas = Canvas(textBitmap)
            canvas.drawBitmap(response1, 0f, 0f, null) // draw the original image
            val paint = Paint()
            paint.color = ContextCompat.getColor(context, R.color.lightgrey)
            paint.textSize = 16f

            var widthTotalDistance = (2 * 3.14 * 6378 * Math.abs(Math.cos(city.latitude / 180 * 3.14)).toDouble() /
                (Math.pow(2.0, zoom.toDouble()) * 256) * 256).toInt()
            var distanceUnit = context.getString(R.string.units_km)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (LocaleData.getMeasurementSystem(ULocale.forLocale(Locale.getDefault())) != LocaleData.MeasurementSystem.SI) {
                    distanceUnit = context.getString(R.string.units_mi)
                    widthTotalDistance = (2 * 3.14 * 6378 * 0.6214 * Math.abs(Math.cos(city.latitude / 180 * 3.14)).toDouble() /
                        (Math.pow(2.0, zoom.toDouble()) * 256) * 256).toInt()
                }
            }

            val widthDistanceMarker = getClosestMarker(widthTotalDistance / 10)
            val widthDistanceMarkerPixel = widthDistanceMarker * 256 / widthTotalDistance

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$widthDistanceMarker $distanceUnit", (10 + widthDistanceMarkerPixel + 10).toFloat(), (240 + 5).toFloat(), paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(StringFormatUtils.formatTimeWithoutZone(context, radarTime), 240f, (240 + 5).toFloat(), paint)

            paint.style = Paint.Style.STROKE
            canvas.drawLine(10f, 240f, (10 + widthDistanceMarkerPixel).toFloat(), 240f, paint)

            val maxI = 100 / widthDistanceMarkerPixel
            for (i in 1..maxI) {
                val radius = i * widthDistanceMarkerPixel
                canvas.drawCircle(128f, 128f, radius.toFloat(), paint)
            }

            paint.style = Paint.Style.FILL
            canvas.drawCircle(128f, 128f, 2f, paint)

            //Round off corners
            val clearPaint = Paint()
            clearPaint.style = Paint.Style.STROKE
            clearPaint.strokeWidth = 20.0f
            clearPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawRoundRect(-10f, -10f, 265f, 265f, 30f, 30f, clearPaint)
            return textBitmap
        }

        private fun getClosestMarker(value: Int): Int {
            val markers = intArrayOf(1, 2, 3, 5, 10, 20, 30, 50, 100)
            var closest = markers[0]
            var minDiff = Math.abs(value - closest)
            for (i in 1 until markers.size) {
                val diff = Math.abs(value - markers[i])
                if (diff < minDiff) {
                    minDiff = diff
                    closest = markers[i]
                }
            }
            return closest
        }
    }
}