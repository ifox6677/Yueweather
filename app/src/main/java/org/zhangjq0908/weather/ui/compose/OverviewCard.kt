package org.zhangjq0908.weather.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.zhangjq0908.weather.database.CurrentWeatherData
import org.zhangjq0908.weather.database.HourlyForecast
import org.zhangjq0908.weather.database.QuarterHourlyForecast
import org.zhangjq0908.weather.ui.Help.StringFormatUtils
import org.zhangjq0908.weather.ui.UiResourceProvider

@Composable
fun OverviewCard(
    currentWeather: CurrentWeatherData,
    hourly: List<HourlyForecast>,
    quarterHourly: List<QuarterHourlyForecast>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDay = currentWeather.isDay(context)

    // tick every 30s so time-dependent selections ("now cast") recompose
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }

    // logic copied from CityWeatherAdapter OVERVIEW binding
    val zoneSec = currentWeather.timeZoneSeconds
    val tzMs = zoneSec * 1000L
    val riseTime = (currentWeather.timeSunrise + zoneSec) * 1000L
    val setTime = (currentWeather.timeSunset + zoneSec) * 1000L
    val updateTime = (currentWeather.timestamp + zoneSec) * 1000L

    // Determine display values: prefer quarterHourly next, else hourly nowCast
    var tempText: String? = null
    var weatherId: Int? = null
    var windSpeed: Float? = null
    var precipForecastText: String? = null

    val sunText: String = if (isPolarSun(currentWeather)) {
        "\u2600\u25B2 --:-- \u25BC --:--"
    } else {
        "\u2600\u25B2 ${StringFormatUtils.formatTimeWithoutZone(context, riseTime)} \u25BC ${StringFormatUtils.formatTimeWithoutZone(context, setTime)}"
    }

    if (quarterHourly.isEmpty()) {
        var nowCast: HourlyForecast? = null
        for (f in hourly) {
            if (kotlin.math.abs(f.forecastTime - nowMs) <= 30 * 60 * 1000) { nowCast = f; break }
        }
        nowCast?.let {
            tempText = StringFormatUtils.formatTemperature(context, it.temperature)
            weatherId = it.weatherID
            windSpeed = it.windSpeed
        }
    } else {
        var next: QuarterHourlyForecast? = null
        for (f in quarterHourly) if (f.forecastTime > nowMs) { next = f; break }
        next?.let { n ->
            tempText = StringFormatUtils.formatTemperature(context, n.temperature)
            weatherId = n.weatherID
            windSpeed = n.windSpeed
            // precip forecast logic (12h window)
            if (n.precipitation > 0) {
                var nextWithout: QuarterHourlyForecast? = null
                var count = 0
                for (f in quarterHourly) {
                    if (f.forecastTime > nowMs && f.precipitation == 0f) {
                        if (count == 0) nextWithout = f
                        count++
                        if (count >= 2) break
                    } else count = 0
                }
                if (nextWithout != null && nextWithout.forecastTime - nowMs <= 12 * 60 * 60 * 1000L) {
                    precipForecastText = "\uD83C\uDF02 ${StringFormatUtils.formatTimeWithoutZone(context, nextWithout.forecastTime + tzMs - 15 * 60 * 1000)}"
                }
            } else {
                var nextPrec: QuarterHourlyForecast? = null
                for (f in quarterHourly) if (f.forecastTime > nowMs && f.precipitation > 0) { nextPrec = f; break }
                if (nextPrec != null && nextPrec.forecastTime - nowMs <= 12 * 60 * 60 * 1000L) {
                    precipForecastText = "\u2614 ${StringFormatUtils.formatTimeWithoutZone(context, nextPrec.forecastTime + tzMs - 15 * 60 * 1000)}"
                }
            }
        }
    }

    val imageRes = weatherId?.let { UiResourceProvider.getImageResourceForWeatherCategory(it, isDay) } ?: org.zhangjq0908.weather.R.drawable.wmo_image_error
    val windIconRes = windSpeed?.let { StringFormatUtils.colorWindSpeedWidget(it) }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // top right update time
            Text(
                text = "(${StringFormatUtils.formatTimeWithoutZone(context, updateTime)})",
                color = Color(0xFFFAFAFA),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
            // center wind icon
            windIconRes?.let {
                Image(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp).align(Alignment.Center)
                )
            }
            // right column with temp + precip, white rounded background like original drawable rounded_corner
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tempText?.let { t ->
                    Text(
                        text = t,
                        color = Color(0xFF024265),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.85f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                precipForecastText?.let { p ->
                    Text(
                        text = p,
                        color = Color(0xFF024265),
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.85f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            // bottom sunrise/sunset
            Text(
                text = sunText,
                color = Color(0xFF024265),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}
