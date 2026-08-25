package org.zhangjq0908.weather.ui.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.zhangjq0908.weather.database.CurrentWeatherData
import org.zhangjq0908.weather.database.DatabaseExecutor
import org.zhangjq0908.weather.database.HourlyForecast
import org.zhangjq0908.weather.database.QuarterHourlyForecast
import org.zhangjq0908.weather.database.SQLiteHelper
import org.zhangjq0908.weather.database.WeekForecast
import java.util.concurrent.atomic.AtomicInteger

class WeatherDataViewModel(application: Application) : AndroidViewModel(application) {

    data class CityWeatherUiState(
        val cityId: Int = -1,
        val cityLatitude: Float = 0f,
        val currentWeather: CurrentWeatherData? = null,
        val hourlyForecasts: List<HourlyForecast> = emptyList(),
        val weekForecasts: List<WeekForecast> = emptyList(),
        val quarterHourly: List<QuarterHourlyForecast> = emptyList(),
        val isLoading: Boolean = true,
        /** wall-clock timestamp of the last completed DB load; used to detect fresh data */
        val lastLoadCompletedAt: Long = 0L
    )

    private val _state = MutableStateFlow(CityWeatherUiState())
    val state: StateFlow<CityWeatherUiState> = _state.asStateFlow()

    private val loadSeq = AtomicInteger(0)
    private var activeCityId = -1

    fun load(cityId: Int) {
        if (cityId == -1) return
        activeCityId = cityId
        val seq = loadSeq.incrementAndGet()
        _state.update { it.copy(cityId = cityId, isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val db = SQLiteHelper.getInstance(getApplication())
            val cur = db.getCurrentWeatherByCityId(cityId)
            val hourly = db.getForecastsByCityId(cityId)
            val week = db.getWeekForecastsByCityId(cityId)
            val quarter = db.getQuarterHourlyForecastsByCityId(cityId)
            val lat = db.getCityToWatch(cityId)?.latitude ?: 0f
            val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
            val filteredHourly = hourly.filter { it.forecastTime >= oneHourAgo }
            if (seq != loadSeq.get()) return@launch
            _state.update {
                it.copy(
                    cityId = cityId,
                    cityLatitude = lat,
                    currentWeather = cur,
                    hourlyForecasts = filteredHourly.ifEmpty { hourly },
                    weekForecasts = week,
                    quarterHourly = quarter,
                    isLoading = false,
                    lastLoadCompletedAt = System.currentTimeMillis()
                )
            }
        }
    }

    /** Single entry point for pushed updates from ViewUpdater (main thread). */
    fun onCurrentWeatherUpdate(data: CurrentWeatherData?) {
        if (data == null || data.city_id != activeCityId) return
        val cur = _state.value
        if (cur.currentWeather == null || cur.currentWeather.timestamp == 0L) {
            load(activeCityId)
        } else {
            _state.update { it.copy(currentWeather = data, isLoading = false) }
        }
    }

    fun onHourlyUpdate(hourlyForecasts: List<HourlyForecast>?) {
        if (hourlyForecasts.isNullOrEmpty() || hourlyForecasts[0].city_id != activeCityId) return
        val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
        _state.update { it.copy(hourlyForecasts = hourlyForecasts.filter { f -> f.forecastTime >= oneHourAgo }) }
    }

    fun onWeekUpdate(forecasts: List<WeekForecast>?) {
        if (forecasts.isNullOrEmpty() || forecasts[0].city_id != activeCityId) return
        _state.update { it.copy(weekForecasts = forecasts) }
    }
}
