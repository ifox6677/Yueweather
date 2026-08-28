package org.zhangjq0908.weather.util

import java.util.Calendar
import java.util.TimeZone

/** Pure time/day-night calculation helpers, safe for JVM unit tests. */
object WeatherTimeUtils {

    /** True when sunrise equals sunset (or a full-day offset), i.e. polar day/night data. Times are Unix seconds. */
    @JvmStatic
    fun isPolarSun(timeSunriseSec: Long, timeSunsetSec: Long): Boolean {
        return (timeSunriseSec - timeSunsetSec) % (24 * 60 * 60L) == 0L
    }

    /**
     * Day/night decision for a forecast point.
     * @param localForecastMs UTC epoch + city timezone offset in ms (i.e. "local wall clock as pseudo-UTC")
     * @param timeSunriseSec  sunrise, Unix seconds, UTC based
     * @param timeSunsetSec   sunset, Unix seconds, UTC based
     * @param timeZoneSeconds city timezone offset in seconds
     */
    @JvmStatic
    fun computeIsDay(
        localForecastMs: Long,
        timeSunriseSec: Long,
        timeSunsetSec: Long,
        timeZoneSeconds: Int,
        cityLatitude: Float
    ): Boolean {
        val forecastTime = Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply { timeInMillis = localForecastMs }
        if (isPolarSun(timeSunriseSec, timeSunsetSec)) {
            return if (cityLatitude > 0) {
                forecastTime.get(Calendar.DAY_OF_YEAR) in 80..265
            } else {
                forecastTime.get(Calendar.DAY_OF_YEAR) !in 80..265
            }
        }
        val tzMs = timeZoneSeconds * 1000L
        val sunRise = Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply {
            timeInMillis = timeSunriseSec * 1000 + tzMs
            set(Calendar.DAY_OF_YEAR, forecastTime.get(Calendar.DAY_OF_YEAR))
            set(Calendar.YEAR, forecastTime.get(Calendar.YEAR))
        }
        val sunSet = Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply {
            timeInMillis = timeSunsetSec * 1000 + tzMs
            set(Calendar.DAY_OF_YEAR, forecastTime.get(Calendar.DAY_OF_YEAR))
            set(Calendar.YEAR, forecastTime.get(Calendar.YEAR))
        }
        return forecastTime.after(sunRise) && forecastTime.before(sunSet)
    }

    /** Same local calendar day (in the pseudo-UTC "local" frame) check. */
    @JvmStatic
    fun isSameLocalDay(t1: Long?, t2: Long?): Boolean {
        if (t1 == null || t2 == null) return false
        val c1 = Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply { timeInMillis = t2 }
        return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
                c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
    }
}
