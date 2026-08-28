package org.zhangjq0908.weather.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.zhangjq0908.weather.R
import org.zhangjq0908.weather.database.CurrentWeatherData
import org.zhangjq0908.weather.database.HourlyForecast
import org.zhangjq0908.weather.database.QuarterHourlyForecast
import org.zhangjq0908.weather.database.WeekForecast
import org.zhangjq0908.weather.ui.Help.StringFormatUtils
import java.util.Date
import java.util.Calendar
import java.util.TimeZone

@Composable
fun WeatherForecastScreen(
    currentWeather: CurrentWeatherData?,
    hourlyForecasts: List<HourlyForecast>,
    weekForecasts: List<WeekForecast>,
    quarterHourly: List<QuarterHourlyForecast>,
    onWeekDayClick: (WeekForecast) -> Unit,
    modifier: Modifier = Modifier,
    hourlyListState: LazyListState = rememberLazyListState(),
    cityLatitude: Float = 0f
) {
    if (currentWeather == null || currentWeather.timestamp == 0L) {
        // Empty view like card_empty; wrapped in a scrollable container so pull-to-refresh still works
        val context = LocalContext.current
        LazyColumn(modifier = modifier.fillMaxSize(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            item(key = "empty") {
                Box(modifier = Modifier.fillParentMaxSize().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF7FB1E2))) {
                        Text(
                            text = context.getString(R.string.card_empty_no_data),
                            color = Color(0xFF024265),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        return
    }

    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    // keep selectedDate in sync with data: reset only if the selected day no longer exists
    LaunchedEffect(weekForecasts) {
        if (weekForecasts.isEmpty()) {
            selectedDate = null
        } else {
            val tzMs = currentWeather.timeZoneSeconds * 1000L
            val stillExists = selectedDate != null && weekForecasts.any {
                isSameLocalDay(it.forecastTime + tzMs, selectedDate?.time)
            }
            if (!stillExists) {
                selectedDate = Date(weekForecasts[0].forecastTime + tzMs)
            }
        }
    }

    // header date for hourly section (driven by scroll position only, independent from week selection)
    var hourlyHeaderDate by remember {
        mutableStateOf(
            Date(hourlyForecasts.firstOrNull()?.let { it.forecastTime + currentWeather.timeZoneSeconds * 1000L }
                ?: System.currentTimeMillis())
        )
    }

    // LazyColumn outer - replaces StaggeredGrid / Linear RecyclerView
    // We add grid layout support: if pref_gridlayout true we could use 2 columns via LazyVerticalGrid
    // For simplicity use LazyColumn vertical
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "overview") {
            OverviewCard(
                currentWeather = currentWeather,
                hourly = hourlyForecasts,
                quarterHourly = quarterHourly,
                cityLatitude = cityLatitude
            )
        }

        // Hourly card
        if (hourlyForecasts.isNotEmpty()) {
            item(key = "hourly_header") {
                // we need to track hourlyHeaderDate to update header text
                // header text derived from hourlyHeaderDate
                val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply { timeInMillis = hourlyHeaderDate.time }
                val dayLong = StringFormatUtils.getDayLong(cal.get(Calendar.DAY_OF_WEEK))
                val dayStr = context.getString(dayLong)
                val dateStr = StringFormatUtils.formatDate(hourlyHeaderDate.time)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7FB1E2))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(
                            text = "$dayStr ($dateStr)",
                            color = Color(0xFF024265),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(6.dp)
                        )
                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White))
                        Spacer(modifier = Modifier.height(4.dp))
                        HourlyForecastRow(
                            forecasts = hourlyForecasts,
                            currentWeather = currentWeather,
                            cityLatitude = cityLatitude,
                            onHeaderDateChange = { hourlyHeaderDate = it },
                            listState = hourlyListState
                        )
                    }
                }
            }
        }

        // Week card
        if (weekForecasts.isNotEmpty()) {
            item(key = "week") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7FB1E2))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(
                            text = context.getString(R.string.card_week_heading),
                            color = Color(0xFF024265),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(6.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        WeeklyForecastRow(
                            forecasts = weekForecasts,
                            currentWeather = currentWeather,
                            cityLatitude = cityLatitude,
                            selectedDate = selectedDate,
                            onDayClick = { wf ->
                                // find hourly index to scroll
                                onWeekDayClick(wf)
                                // also update selected
                                selectedDate = Date(wf.forecastTime + currentWeather.timeZoneSeconds * 1000L)
                            }
                        )
                    }
                }
            }
        }

        // Chart card - Compose Canvas replacing williamchart
        if (weekForecasts.isNotEmpty()) {
            item(key = "chart") {
                WeeklyChart(
                    weekForecasts = weekForecasts,
                    timeZoneSeconds = currentWeather.timeZoneSeconds.toLong()
                )
            }
        }
    }
}

private fun isSameLocalDay(t1: Long?, t2: Long?): Boolean {
    return org.zhangjq0908.weather.util.WeatherTimeUtils.isSameLocalDay(t1, t2)
}
