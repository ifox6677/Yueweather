package org.zhangjq0908.weather.ui.compose


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.preference.PreferenceManager

import org.zhangjq0908.weather.database.WeekForecast
import org.zhangjq0908.weather.preferences.AppPreferencesManager
import org.zhangjq0908.weather.ui.Help.StringFormatUtils
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compose replacement for williamchart LineChartView + BarChartView
 * Draws temperature max/min as smooth lines with filled range,
 * precipitation as bars, freezing line, and dual Y axes.
 */
@Composable
fun WeeklyChart(
    weekForecasts: List<WeekForecast>,
    //modifier: Modifier = Modifier,
    timeZoneSeconds: Long = 0L
) {
    if (weekForecasts.isEmpty()) return
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val prefManager = remember(prefs) { AppPreferencesManager(prefs) }

    // ---- compute data similar to original CityWeatherAdapter CHART block ----
    // All derived chart data is cached per forecast list to avoid recompute on every recomposition
    val chartData = remember(weekForecasts, prefManager, timeZoneSeconds, context) {
        buildWeeklyChartData(context, weekForecasts, prefs, prefManager, timeZoneSeconds)
    }
    with(chartData) {
        ChartBody(
            weekForecasts = weekForecasts,
            tempsMax = tempsMax,
            tempsMin = tempsMin,
            precips = precips,
            showFreezing = showFreezing,
            yMinTemp = yMinTemp,
            mid = mid,
            stepTemp = stepTemp,
            stepNum = stepNum,
            stepPrec = stepPrec,
            yMaxPrec = yMaxPrec,
            isInch = isInch,
            dayLabels = dayLabels,
            temperatureUnitLabel = prefManager.temperatureUnit,
            precipitationUnitLabel = prefManager.getPrecipitationUnit(context),
            convertFromCelsius = { prefManager.convertTemperatureFromCelsius(it) }
        )
    }
}

private class WeeklyChartData(
    val tempsMax: List<Float>,
    val tempsMin: List<Float>,
    val precips: List<Float>,
    val showFreezing: Boolean,
    val yMinTemp: Int,
    val mid: Int,
    val stepTemp: Int,
    val stepNum: Int,
    val stepPrec: Int,
    val yMaxPrec: Int,
    val isInch: Boolean,
    val dayLabels: List<String>
)

private fun buildWeeklyChartData(
    context: android.content.Context,
    weekForecasts: List<WeekForecast>,
    prefs: android.content.SharedPreferences,
    prefManager: AppPreferencesManager,
    timeZoneSeconds: Long
): WeeklyChartData {
    run {
        // Pre-calc temps/precip with unit conversion
        val tempsMax = weekForecasts.map { prefManager.convertTemperatureFromCelsius(it.maxTemperature) }
        val tempsMin = weekForecasts.map { prefManager.convertTemperatureFromCelsius(it.minTemperature) }
        val precips = weekForecasts.map { prefManager.convertPrecipitationFromMM(it.precipitation) }

        var tMin = tempsMin.minOrNull() ?: 0f
        var tMax = tempsMax.maxOrNull() ?: 0f
        val pMax = precips.maxOrNull() ?: 0f
        // original logic: showFreezing if tmin<0 && !pref_apparentTemp
        val showFreezing = tMin < 0 && !prefs.getBoolean("pref_apparentTemp", false)
        tMax += 1f
        tMin -= 1f
        val mid = ((tMin + tMax) / 2f).roundToInt()
        val stepTemp = max(1, ceil(kotlin.math.abs(tMax - tMin) / 4.0).toInt())
        val yMinTemp = mid - 2 * stepTemp
        //val yMaxTemp = mid + 2 * stepTemp

        val isInch = prefs.getString("precipitationUnit", "1") != "1"
        val stepNum = if (isInch) 2 else 4
        val pReference = max(prefManager.convertPrecipitationFromMM(10f), pMax * 2)
        val stepPrec = ceil(pReference / stepNum).toInt().coerceAtLeast(1)
        val yMaxPrec = stepPrec * stepNum

        // day labels
        val dayLabels = weekForecasts.map { wf ->
            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            cal.timeInMillis = wf.forecastTime + timeZoneSeconds * 1000L
            val day = cal.get(Calendar.DAY_OF_WEEK)
            var s = context.getString(StringFormatUtils.getDayShort(day))
            if (weekForecasts.size > 8) s = s.substring(0, 1)
            s
        }

        return WeeklyChartData(
            tempsMax = tempsMax,
            tempsMin = tempsMin,
            precips = precips,
            showFreezing = showFreezing,
            yMinTemp = yMinTemp,
            mid = mid,
            stepTemp = stepTemp,
            stepNum = stepNum,
            stepPrec = stepPrec,
            yMaxPrec = yMaxPrec,
            isInch = isInch,
            dayLabels = dayLabels
        )
    }
}

@Composable
private fun ChartBody(
    weekForecasts: List<WeekForecast>,
    tempsMax: List<Float>,
    tempsMin: List<Float>,
    precips: List<Float>,
    showFreezing: Boolean,
    yMinTemp: Int,
    mid: Int,
    stepTemp: Int,
    stepNum: Int,
    stepPrec: Int,
    yMaxPrec: Int,
    isInch: Boolean,
    dayLabels: List<String>,
    temperatureUnitLabel: String,
    precipitationUnitLabel: String,
    convertFromCelsius: (Float) -> Float,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF7FB1E2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = " $temperatureUnitLabel ",
                    color = androidx.compose.ui.graphics.Color(0xFF024265),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(4.dp)
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(org.zhangjq0908.weather.R.string.chart),
                    color = androidx.compose.ui.graphics.Color(0xFF024265),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = " $precipitationUnitLabel ",
                    color = androidx.compose.ui.graphics.Color(0xFF024265),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val textMeasurer = rememberTextMeasurer()
            // Canvas height 200dp like original
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val w = size.width
                val h = size.height
                // margins for labels
                val leftMargin = 36.dp.toPx()
                val rightMargin = 36.dp.toPx()
                val bottomMargin = 20.dp.toPx()
                val topMargin = 8.dp.toPx()
                val chartW = w - leftMargin - rightMargin
                val chartH = h - topMargin - bottomMargin

                // helpers
                val yMaxTemp = mid + 2 * stepTemp
                fun tempToY(temp: Float): Float {
                    val ratio = (yMaxTemp - temp) / (yMaxTemp - yMinTemp).toFloat()
                    return topMargin + ratio * chartH
                }
                fun precToY(prec: Float): Float {
                    val ratio = prec / yMaxPrec.toFloat()
                    return topMargin + chartH - ratio * chartH
                }
                fun xForIndex(i: Int): Float {
                    // Need to handle bar alignment: original added 2 bars in middle for alignment.
                    // For Compose we simplify: evenly distribute n points
                    // But keep precipitation bar centering logic similar to original double bars
                    // Original: for week.size>?? they did 1 bar at ends, 2 bars middle -> total bars = n + (n-2)
                    // For n=7, total bars = 12 , x spacing non-uniform. We approximate with uniform spacing and bar width = chartW/(n*1.5)
                    // Simpler: use uniform x for temperature points
                    if (weekForecasts.size == 1) return leftMargin + chartW / 2f
                    return leftMargin + (i.toFloat() / (weekForecasts.size - 1).coerceAtLeast(1)) * chartW
                }

                // draw grid horizontal lines
                val gridColor = Color(0x22FFFFFF)
                for (k in 0..4) {
                    val y = topMargin + k * chartH / 4f
                    drawLine(gridColor, Offset(leftMargin, y), Offset(w - rightMargin, y), strokeWidth = 1f)
                }

                // -- Precipitation bars --
                val barColor = Color(0xFF1B4CF0).copy(alpha = 0.6f)
                // bar width
                val barWidth = if (weekForecasts.size > 1) chartW / (weekForecasts.size * 2.5f) else chartW * 0.15f
                dayLabels.forEachIndexed { i, _ ->
                    val x = xForIndex(i)
                    val prec = precips[i]
                    val yTop = precToY(prec)
                    val yBottom = topMargin + chartH
                    // draw bar centered at x
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x - barWidth / 2f, yTop),
                        size = androidx.compose.ui.geometry.Size(barWidth, yBottom - yTop)
                    )
                }

                // -- Temperature lines with smooth cubic and fill --
                // Build points
                val maxPoints = tempsMax.mapIndexed { i, v -> Offset(xForIndex(i), tempToY(v)) }
                val minPoints = tempsMin.mapIndexed { i, v -> Offset(xForIndex(i), tempToY(v)) }

                // Fill between curves
                if (maxPoints.size >= 2) {
                    val fillPath = Path().apply {
                        moveTo(maxPoints[0].x, maxPoints[0].y)
                        // cubic approx using quadratic smoothing
                        for (i in 1 until maxPoints.size) {
                            val prev = maxPoints[i - 1]
                            val cur = maxPoints[i]
                            val midX = (prev.x + cur.x) / 2f
                            cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                        }
                        // down to min curve reverse
                        for (i in minPoints.size - 1 downTo 0) {
                            val cur = minPoints[i]
                            if (i == minPoints.size - 1) lineTo(cur.x, cur.y) else {
                                val next = minPoints[i + 1]
                                val midX = (cur.x + next.x) / 2f
                                cubicTo(midX, next.y, midX, cur.y, cur.x, cur.y)
                            }
                        }
                        close()
                    }
                    drawPath(fillPath, color = Color(0xFFA8A8A8).copy(alpha = 0.35f))
                    // also draw background under min to hide? original used backgroundBlue fill for min, we just use above
                }

                // Freezing line at 0C converted
                if (showFreezing) {
                    val y0 = tempToY(convertFromCelsius(0f))
                    val dash = floatArrayOf(10f, 10f)
                    drawDashedLine(
                        color = Color(0xFFAAAAAA),
                        start = Offset(leftMargin, y0),
                        end = Offset(w - rightMargin, y0),
                        strokeWidth = 3f,
                        dash = dash
                    )
                }

                // Draw max line (red)
                drawSmoothLine(maxPoints, color = Color(0xFFE01530), strokeWidth = 4f)
                // Draw min line (blue)
                drawSmoothLine(minPoints, color = Color(0xFF1B4CF0), strokeWidth = 4f)
                // X-axis line at yMinTemp (mid-2*step)
                val yAxis = tempToY(yMinTemp.toFloat())
                drawLine(Color(0xFF024265), Offset(leftMargin, yAxis), Offset(w - rightMargin, yAxis), strokeWidth = 3f)

                // --- Y labels ---
                val labelStyle = TextStyle(color = Color(0xFF024265), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                // left temp labels: yMin, mid, yMax etc (original shows step labels inside)
                // only draw 3-5 values to avoid clutter, use step
                for (idx in 0..4) {
                    val tVal = yMinTemp + idx * stepTemp
                    val y = tempToY(tVal.toFloat())
                    // draw tick maybe not needed
                    val txt = tVal.toString()
                    val layout = textMeasurer.measure(txt, style = labelStyle)
                    drawText(layout, topLeft = Offset(leftMargin - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f))
                }
                // right precip labels
                for (idx in 0..stepNum) {
                    val pVal = idx * stepPrec
                    val y = precToY(pVal.toFloat())
                    val txt = if (isInch) String.format("%.2f", pVal.toFloat() / 1f) else pVal.toString()
                    // original shows mm values; for inch show with 2 decimals
                    val display = if (pVal == 0) "0" else txt
                    val layout = textMeasurer.measure(display, style = labelStyle)
                    drawText(layout, topLeft = Offset(w - rightMargin + 4.dp.toPx(), y - layout.size.height / 2f))
                }
                // X day labels
                dayLabels.forEachIndexed { i, label ->
                    val x = xForIndex(i)
                    val layout = textMeasurer.measure(label, style = labelStyle.copy(fontSize = 13.sp))
                    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, h - bottomMargin + 4.dp.toPx()))
                }
            }
        }
    }
}

private fun DrawScope.drawSmoothLine(points: List<Offset>, color: Color, strokeWidth: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val midX = (p0.x + p1.x) / 2f
            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
    }
    drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawDashedLine(color: Color, start: Offset, end: Offset, strokeWidth: Float, dash: FloatArray) {
    val path = Path().apply { moveTo(start.x, start.y); lineTo(end.x, end.y) }
    drawPath(
        path,
        color = color,
        style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(dash, 0f))
    )
}
