package org.zhangjq0908.weather.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WeatherTimeUtilsTest {

    private fun gmtUtcMs(year: Int, dayOfYear: Int, hour: Int, minute: Int = 0): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.DAY_OF_YEAR, dayOfYear)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // ---------- isPolarSun ----------

    @Test
    fun isPolarSun_trueWhenRiseEqualsSet() {
        assertTrue(WeatherTimeUtils.isPolarSun(1000L, 1000L))
        assertTrue(WeatherTimeUtils.isPolarSun(0L, 0L))
    }

    @Test
    fun isPolarSun_trueWhenExactlyOneDayApart() {
        assertTrue(WeatherTimeUtils.isPolarSun(100_000L, 100_000L + 86_400_000L))
    }

    @Test
    fun isPolarSun_falseForNormalDay() {
        assertFalse(WeatherTimeUtils.isPolarSun(6 * 3600L, 18 * 3600L))
        assertFalse(WeatherTimeUtils.isPolarSun(0L, 43_200_000L - 1))
    }

    // ---------- computeIsDay (normal latitudes) ----------

    private val riseSec = gmtUtcMs(2024, 150, 5) / 1000   // 05:00 UTC
    private val setSec = gmtUtcMs(2024, 150, 19) / 1000   // 19:00 UTC

    @Test
    fun computeIsDay_trueBetweenSunriseAndSunset() {
        // 12:00 local (=UTC, tz=0) on the same day
        val noonLocal = gmtUtcMs(2024, 150, 12)
        assertTrue(WeatherTimeUtils.computeIsDay(noonLocal, riseSec, setSec, 0, 50f))
    }

    @Test
    fun computeIsDay_falseBeforeSunriseAndAfterSunset() {
        assertFalse(WeatherTimeUtils.computeIsDay(gmtUtcMs(2024, 150, 3), riseSec, setSec, 0, 50f))
        assertFalse(WeatherTimeUtils.computeIsDay(gmtUtcMs(2024, 150, 21), riseSec, setSec, 0, 50f))
    }

    @Test
    fun computeIsDay_respectsTimezoneOffset() {
        // city at UTC+2: sunrise 05:00 UTC = 07:00 local, sunset 19:00 UTC = 21:00 local (same local day)
        val tzSeconds = 2 * 3600
        assertFalse(WeatherTimeUtils.computeIsDay(gmtUtcMs(2024, 150, 5), riseSec, setSec, tzSeconds, 50f))   // 05:00 local < 07:00
        assertTrue(WeatherTimeUtils.computeIsDay(gmtUtcMs(2024, 150, 8), riseSec, setSec, tzSeconds, 50f))    // 08:00 local > 07:00
        assertTrue(WeatherTimeUtils.computeIsDay(gmtUtcMs(2024, 150, 20), riseSec, setSec, tzSeconds, 50f))   // 20:00 local < 21:00
        assertFalse(WeatherTimeUtils.computeIsDay(gmtUtcMs(2024, 150, 22), riseSec, setSec, tzSeconds, 50f))  // 22:00 local > 21:00
    }

    // ---------- computeIsDay (polar) ----------

    @Test
    fun polar_northernHemisphereSummerIsDay() {
        val polarRise = 100L
        val midSummerLocal = gmtUtcMs(2024, 172, 12) // ~June 20th
        assertTrue(WeatherTimeUtils.computeIsDay(midSummerLocal, polarRise, polarRise, 0, 80f))
    }

    @Test
    fun polar_northernHemisphereWinterIsNight() {
        val polarRise = 100L
        val midWinterLocal = gmtUtcMs(2024, 15, 12)
        assertFalse(WeatherTimeUtils.computeIsDay(midWinterLocal, polarRise, polarRise, 0, 80f))
    }

    @Test
    fun polar_southernHemisphereIsInverted() {
        val polarRise = 100L
        val midSummerNorth = gmtUtcMs(2024, 172, 12)
        assertFalse(WeatherTimeUtils.computeIsDay(midSummerNorth, polarRise, polarRise, 0, -80f))
        val midWinterNorth = gmtUtcMs(2024, 15, 12)
        assertTrue(WeatherTimeUtils.computeIsDay(midWinterNorth, polarRise, polarRise, 0, -80f))
    }

    // ---------- isSameLocalDay ----------

    @Test
    fun isSameLocalDay_nullHandling() {
        assertFalse(WeatherTimeUtils.isSameLocalDay(null, 0L))
        assertFalse(WeatherTimeUtils.isSameLocalDay(0L, null))
        assertFalse(WeatherTimeUtils.isSameLocalDay(null, null))
    }

    @Test
    fun isSameLocalDay_sameAndDifferentDays() {
        val d1Morning = gmtUtcMs(2024, 100, 1)
        val d1Evening = gmtUtcMs(2024, 100, 23)
        val d2Noon = gmtUtcMs(2024, 101, 12)
        val nextYear = gmtUtcMs(2025, 100, 12)
        assertTrue(WeatherTimeUtils.isSameLocalDay(d1Morning, d1Evening))
        assertFalse(WeatherTimeUtils.isSameLocalDay(d1Morning, d2Noon))
        assertFalse(WeatherTimeUtils.isSameLocalDay(d1Morning, nextYear))
    }
}
