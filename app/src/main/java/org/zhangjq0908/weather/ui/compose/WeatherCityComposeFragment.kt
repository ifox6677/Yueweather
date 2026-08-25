package org.zhangjq0908.weather.ui.compose

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.zhangjq0908.weather.activities.ForecastCityActivity
import org.zhangjq0908.weather.database.CurrentWeatherData
import org.zhangjq0908.weather.database.HourlyForecast
import org.zhangjq0908.weather.database.QuarterHourlyForecast
import org.zhangjq0908.weather.database.WeekForecast
import org.zhangjq0908.weather.ui.updater.IUpdateableCityUI
import org.zhangjq0908.weather.ui.updater.ViewUpdater
import org.zhangjq0908.weather.ui.viewPager.WeatherPagerAdapter
import androidx.compose.foundation.lazy.rememberLazyListState

class WeatherCityComposeFragment : Fragment(), IUpdateableCityUI {

    private var cityId: Int = -1
    private val viewModel: WeatherDataViewModel by viewModels()

    companion object {
        @JvmStatic
        fun newInstance(args: Bundle): WeatherCityComposeFragment {
            val f = WeatherCityComposeFragment()
            f.arguments = args
            return f
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        ViewUpdater.addSubscriber(this)
    }

    override fun onDetach() {
        ViewUpdater.removeSubscriber(this)
        super.onDetach()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cityId = arguments?.getInt("city_id") ?: -1
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel.load(cityId)

        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.state.collectAsState()

                val pullState = rememberPullToRefreshState()
                // baseline of lastLoadCompletedAt when the pull started; null = not refreshing
                var refreshBaseline by remember { mutableStateOf<Long?>(null) }

                // M3 automatically enters refreshing state once released past the threshold
                LaunchedEffect(pullState.isRefreshing) {
                    if (pullState.isRefreshing && refreshBaseline == null) {
                        refreshBaseline = uiState.lastLoadCompletedAt
                        triggerRefresh()
                        viewModel.load(cityId)
                    }
                }

                // hide the indicator once fresh data (a new completed load) has arrived
                LaunchedEffect(refreshBaseline, uiState.lastLoadCompletedAt) {
                    val baseline = refreshBaseline ?: return@LaunchedEffect
                    if (uiState.lastLoadCompletedAt != baseline) {
                        refreshBaseline = null
                        pullState.endRefresh()
                    }
                }

                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(pullState.nestedScrollConnection)
                    ) {
                        val hourlyListState = rememberLazyListState()

                        // Need to expose scroll action for week click
                        var pendingScroll by remember { mutableStateOf<Int?>(null) }
                        LaunchedEffect(pendingScroll) {
                            pendingScroll?.let { idx ->
                                hourlyListState.animateScrollToItem(idx)
                                pendingScroll = null
                            }
                        }

                        if (uiState.isLoading && uiState.currentWeather == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            WeatherForecastScreen(
                                currentWeather = uiState.currentWeather,
                                cityLatitude = uiState.cityLatitude,
                                hourlyForecasts = uiState.hourlyForecasts,
                                weekForecasts = uiState.weekForecasts,
                                quarterHourly = uiState.quarterHourly,
                                onWeekDayClick = { wf ->
                                    val targetTime = wf.forecastTime - 6 * 3600000L
                                    var idx = -1
                                    for (i in uiState.hourlyForecasts.indices) {
                                        if (uiState.hourlyForecasts[i].forecastTime > targetTime) { idx = i; break }
                                    }
                                    if (idx != -1) {
                                        pendingScroll = idx
                                    }
                                },
                                hourlyListState = hourlyListState
                            )
                        }

                        PullToRefreshContainer(
                            state = pullState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
        return composeView
    }

    private fun triggerRefresh() {
        WeatherPagerAdapter.refreshSingleData(requireContext(), true, cityId)
        (activity as? ForecastCityActivity)?.startRefreshAnimation()
    }

    // IUpdateableCityUI callbacks - delegate to ViewModel (single source of truth)

    override fun processNewCurrentWeatherData(data: CurrentWeatherData?) {
        viewModel.onCurrentWeatherUpdate(data)
    }

    override fun processNewForecasts(hourlyForecasts: List<HourlyForecast>?) {
        viewModel.onHourlyUpdate(hourlyForecasts)
    }

    override fun processNewWeekForecasts(forecasts: List<WeekForecast>?) {
        viewModel.onWeekUpdate(forecasts)
    }
}
