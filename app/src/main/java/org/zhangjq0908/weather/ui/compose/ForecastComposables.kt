package org.zhangjq0908.weather.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import androidx.compose.foundation.Image
import org.zhangjq0908.weather.R
import org.zhangjq0908.weather.database.CurrentWeatherData
import org.zhangjq0908.weather.database.HourlyForecast
import org.zhangjq0908.weather.database.WeekForecast
import org.zhangjq0908.weather.ui.Help.StringFormatUtils
import org.zhangjq0908.weather.ui.UiResourceProvider
import java.util.Calendar
import java.util.TimeZone
import java.util.Date

// ---------- Hourly ----------

@Composable
fun HourlyForecastRow(
    forecasts: List<HourlyForecast>,
    currentWeather: CurrentWeatherData,
    cityLatitude: Float,
    onHeaderDateChange: (Date) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    if (forecasts.isEmpty()) return
    val context = LocalContext.current
    val deduped = remember(forecasts) { forecasts.distinctBy { it.city_id to it.forecastTime } }
    // read prefs once per row instead of per item composition
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val showPressure = prefs.getBoolean("pref_showPressure", false)
    val showUv = prefs.getBoolean("pref_showHourlyUvIndex", false)

    // update header based on first visible item (like original CourseOfDayAdapter.updateRecyclerViewHeader)
    LaunchedEffect(listState.firstVisibleItemIndex, forecasts, currentWeather.timestamp) {
        val idx = listState.firstVisibleItemIndex.coerceIn(0, forecasts.size - 1)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.timeInMillis = forecasts[idx].forecastTime + currentWeather.timeZoneSeconds * 1000L
        onHeaderDateChange(cal.time)
    }

    Column(modifier = modifier) {
        // header is handled outside, but we also provide internal monitoring
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(deduped, key = { "${it.city_id}_${it.forecastTime}" }) { item ->
                HourlyItem(item = item, current = currentWeather, latitude = cityLatitude, showPressure = showPressure, showUv = showUv)
            }
        }
    }
}

@Composable
fun HourlyItem(item: HourlyForecast, current: CurrentWeatherData, latitude: Float, showPressure: Boolean, showUv: Boolean) {
    val context = LocalContext.current
    val isDay = remember(item.forecastTime, current.timestamp, latitude) {
        computeIsDayForHourly(item, current, latitude)
    }

    Column(
        modifier = Modifier
            .width(86.dp)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = StringFormatUtils.formatTimeWithoutZone(context, item.forecastTime + current.timeZoneSeconds * 1000L),
            color = Color(0xFF024265),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = UiResourceProvider.getIconResourceForWeatherCategory(item.weatherID, isDay)),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
            // wind icon overlay small - use drawable id via colorWindSpeedWidget
            val windRes = StringFormatUtils.colorWindSpeedWidget(item.windSpeed)
            androidx.compose.foundation.Image(
                painter = painterResource(id = windRes),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = StringFormatUtils.formatTemperature(context, item.temperature), color = Color(0xFF024265), fontSize = 14.sp)
        Text(text = StringFormatUtils.formatInt(item.humidity, context.getString(R.string.units_rh)), color = Color(0xFF024265), fontSize = 13.sp)
        if (showPressure) {
            if (item.pressure != -1f) {
                Text(text = StringFormatUtils.formatInt(item.pressure, context.getString(R.string.units_hPa)), color = Color(0xFF024265), fontSize = 13.sp)
            }
        }
        Text(
            text = if (item.precipitation == 0f) "-" else StringFormatUtils.formatPrecipitation(context, item.precipitation),
            color = Color(0xFF024265), fontSize = 13.sp
        )
        // wind speed with background
        val windBg = windBackgroundColor(item.windSpeed)
        Text(
            text = StringFormatUtils.formatWindSpeed(context, item.windSpeed),
            color = Color(0xFF024265),
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(windBg)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.ic_south_24px),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .rotate(item.windDirection)
        )
        if (showUv) {
            if (item.uvIndex != -1f) {
                val uv = item.uvIndex.roundToIntCompat()
                val uvBg = uvBackgroundColor(uv)
                Text(
                    text = "UV ${StringFormatUtils.formatInt(uv.toFloat())}",
                    color = Color(0xFF024265),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(uvBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun computeIsDayForHourly(f: HourlyForecast, current: CurrentWeatherData, cityLatitude: Float): Boolean {
    return computeIsDay(f.forecastTime + current.timeZoneSeconds * 1000L, current, cityLatitude)
}

// ---------- Weekly ----------

@Composable
fun WeeklyForecastRow(
    forecasts: List<WeekForecast>,
    currentWeather: CurrentWeatherData,
    cityLatitude: Float,
    selectedDate: Date?,
    onDayClick: (WeekForecast) -> Unit,
    modifier: Modifier = Modifier
) {
    if (forecasts.isEmpty()) return
    val deduped = remember(forecasts) { forecasts.distinctBy { it.city_id to it.forecastTime } }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(deduped, key = { "${it.city_id}_${it.forecastTime}" }) { wf ->
            WeeklyItem(
                weekForecast = wf,
                current = currentWeather,
                latitude = cityLatitude,
                isHighlighted = isSameDay(wf.forecastTime + currentWeather.timeZoneSeconds * 1000L, selectedDate?.time),
                onClick = { onDayClick(wf) }
            )
        }
    }
}

@Composable
fun WeeklyItem(
    weekForecast: WeekForecast,
    current: CurrentWeatherData,
    latitude: Float,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cal = remember(weekForecast.forecastTime, current.timeZoneSeconds) {
        Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply {
            timeInMillis = weekForecast.forecastTime + current.timeZoneSeconds * 1000L
        }
    }
    val isDay = remember(current.timestamp, weekForecast.forecastTime, latitude) {
        computeIsDay(cal.timeInMillis, current, latitude)
    }
    val bg = if (isHighlighted) Color(0xFF6FA1D2) else Color.Transparent
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(86.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val day = cal.get(Calendar.DAY_OF_WEEK)
        Text(text = context.getString(StringFormatUtils.getDayShort(day)), color = Color(0xFF024265), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = UiResourceProvider.getIconResourceForWeatherCategory(weekForecast.weatherID, isDay)),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
            val windRes = StringFormatUtils.colorWindSpeedWidget(weekForecast.wind_speed)
            androidx.compose.foundation.Image(
                painter = painterResource(id = windRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp).align(Alignment.BottomEnd).offset(x = 6.dp, y = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = StringFormatUtils.formatTemperature(context, weekForecast.maxTemperature), color = Color(0xFFE01530), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = StringFormatUtils.formatTemperature(context, weekForecast.minTemperature), color = Color(0xFF1B4CF0), fontSize = 14.sp)
        Text(text = if (weekForecast.precipitation == 0f) "-" else StringFormatUtils.formatPrecipitation(context, weekForecast.precipitation), color = Color(0xFF024265), fontSize = 13.sp)
        val windBg = windBackgroundColor(weekForecast.wind_speed)
        Text(
            text = StringFormatUtils.formatWindSpeed(context, weekForecast.wind_speed),
            color = Color(0xFF024265), fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(windBg).padding(horizontal = 6.dp, vertical = 2.dp)
        )
        if (weekForecast.uv_index != -1f) {
            val uv = weekForecast.uv_index.roundToIntCompat()
            val uvBg = uvBackgroundColor(uv)
            Text(
                text = "UV ${StringFormatUtils.formatInt(uv.toFloat())}",
                color = Color(0xFF024265), fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(uvBg).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(text = "\u2600\uFE0E\u200A${weekForecast.sunshineHours.roundToIntCompat()}\u200A${context.getString(R.string.units_hours)}", color = Color(0xFF024265), fontSize = 12.sp)
    }
}

// ---------- helpers ----------

/** True when sunrise equals sunset (or a full-day offset), i.e. polar day/night data. */
internal fun isPolarSun(current: CurrentWeatherData): Boolean {
    return org.zhangjq0908.weather.util.WeatherTimeUtils.isPolarSun(current.timeSunrise, current.timeSunset)
}

/** Shared day/night calculation for hourly and weekly items. localForecastMs must already include the city timezone offset. */
internal fun computeIsDay(localForecastMs: Long, current: CurrentWeatherData, cityLatitude: Float): Boolean {
    return org.zhangjq0908.weather.util.WeatherTimeUtils.computeIsDay(
        localForecastMs, current.timeSunrise, current.timeSunset, current.timeZoneSeconds, cityLatitude
    )
}

private fun windBackgroundColor(speed: Float): Color {
    return when {
        speed < 10.7f -> Color.Transparent
        speed < 17.1f -> Color(0xFFF8F49F)
        speed < 24.4f -> Color(0xFFFEC58E)
        speed < 32.6f -> Color(0xFFFA7972)
        else -> Color(0xFFE01530)
    }
}

private fun uvBackgroundColor(uv: Int): Color {
    return when {
        uv <= 2 -> Color.Transparent
        uv <= 5 -> Color(0xFFF8F49F)
        uv <= 7 -> Color(0xFFFEC58E)
        uv <= 10 -> Color(0xFFFA7972)
        else -> Color(0x779461C9)
    }
}

private fun isSameDay(t1: Long, t2: Long?): Boolean {
    return org.zhangjq0908.weather.util.WeatherTimeUtils.isSameLocalDay(t1, t2)
}

private fun Float.roundToIntCompat(): Int = kotlin.math.round(this).toInt()
private fun Double.roundToIntCompat(): Int = kotlin.math.round(this).toInt()
