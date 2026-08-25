package org.zhangjq0908.weather.weather_api.open_meteo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.zhangjq0908.weather.database.CurrentWeatherData;
import org.zhangjq0908.weather.database.HourlyForecast;
import org.zhangjq0908.weather.database.QuarterHourlyForecast;
import org.zhangjq0908.weather.database.WeekForecast;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OMDataExtractorTest {

    private final OMDataExtractor extractor = new OMDataExtractor(false);
    private final OMDataExtractor snowExtractor = new OMDataExtractor(true);

    private static JSONArray jsonArray(Number... values) {
        JSONArray array = new JSONArray();
        for (Number v : values) array.put(v);
        return array;
    }

    @Test
    public void currentWeatherHappyPath() throws Exception {
        JSONObject json = new JSONObject();
        json.put("weathercode", 3);
        json.put("temperature", 21.5);
        json.put("windspeed", 3.2);
        json.put("winddirection", 180);

        CurrentWeatherData data = extractor.extractCurrentWeather(json.toString());

        assertNotNull(data);
        assertEquals(21.5f, data.getTemperatureCurrent(), 0.001f);
        assertEquals(3.2f, data.getWindSpeed(), 0.001f);
        assertEquals(180f, data.getWindDirection(), 0.001f);
        assertEquals(0L, (long) data.getTimeSunrise());
        assertEquals(0L, (long) data.getTimeSunset());
    }

    @Test
    public void currentWeatherMalformedJsonReturnsNull() {
        assertNull(extractor.extractCurrentWeather("{not-json"));
    }

    private String hourlyPayload(String weatherCodeAt1) {
        return "{\"time\":[1700000000,1700003600,1700007200]," +
                "\"weather_code\":[\"0\",\"" + weatherCodeAt1 + "\",\"61\"]," +
                "\"temperature_2m\":[10.0,11.5,12.25]," +
                "\"relative_humidity_2m\":[50,60,70]," +
                "\"wind_speed_10m\":[1.0,2.0,3.0]}";
    }

    @Test
    public void hourlyForecastMapsFieldsAndConvertsToMillis() {
        List<HourlyForecast> list = extractor.extractHourlyForecast(hourlyPayload("1"));

        assertNotNull(list);
        assertEquals(3, list.size());
        HourlyForecast first = list.get(0);
        assertEquals(1700000000L * 1000L, first.getForecastTime());
        assertEquals(10.0f, first.getTemperature(), 0.001f);
        assertEquals(50f, first.getHumidity(), 0.001f);
        assertEquals(1.0f, first.getWindSpeed(), 0.001f);
    }

    @Test
    public void hourlyForecastStopsAtNullWeatherCode() {
        List<HourlyForecast> list = extractor.extractHourlyForecast(hourlyPayload("null"));

        assertNotNull(list);
        assertEquals(1, list.size());  //iteration breaks at the null entry
    }

    @Test
    public void hourlyPrecipitationPlainMode() {
        String payload = "{\"time\":[1700000000],\"weather_code\":[\"61\"]," +
                "\"precipitation\":[1.5]}";

        List<HourlyForecast> list = extractor.extractHourlyForecast(payload);

        assertEquals(1, list.size());
        assertEquals(1.5f, list.get(0).getPrecipitation(), 0.001f);  //no cm->mm conversion in plain mode
    }

    @Test
    public void hourlyPrecipitationSnowModeSumsRainShowersAndSnowfall() {
        String payload = "{\"time\":[1700000000],\"weather_code\":[\"71\"]," +
                "\"snowfall\":[0.5],\"rain\":[0.2],\"showers\":[0.3]}";

        List<HourlyForecast> list = snowExtractor.extractHourlyForecast(payload);

        assertEquals(1, list.size());
        //snowfall is reported in cm and converted to mm (*10): 5 + 0.2 + 0.3 = 5.5
        assertEquals(5.5f, list.get(0).getPrecipitation(), 0.001f);
    }

    private String weekPayload() {
        return "{\"time\":[1700000000,1700086400]," +
                "\"weather_code\":[\"2\",\"80\"]," +
                "\"temperature_2m_max\":[20.0,21.0]," +
                "\"temperature_2m_min\":[10.0,11.0]," +
                "\"sunrise\":[1699970000,1700056400]," +
                "\"sunset\":[1700010000,1700096400]," +
                "\"sunshine_duration\":[36000,18000]," +
                "\"precipitation_sum\":[0.0,2.5]," +
                "\"wind_speed_10m_max\":[4.0,5.0]}";
    }

    @Test
    public void weekForecastShiftsToMiddayAndDefaultsUvIndex() {
        List<WeekForecast> list = extractor.extractWeekForecast(weekPayload());

        assertNotNull(list);
        assertEquals(2, list.size());
        WeekForecast first = list.get(0);
        assertEquals((1700000000L + 12 * 3600) * 1000L, first.getForecastTime());  //shifted to midday
        assertEquals(-1f, first.getUv_index(), 0.001f);  //uv_index_max missing -> sentinel
        assertEquals(10f, first.getSunshineHours(), 0.001f);  //36000s / 3600
        assertEquals(20.0f, first.getMaxTemperature(), 0.001f);
        assertEquals(10.0f, first.getMinTemperature(), 0.001f);
    }

    @Test
    public void quarterHourlyExtraction() {
        String payload = "{\"time\":[1700000000,1700000900]," +
                "\"weather_code\":[\"61\",\"63\"]," +
                "\"temperature_2m\":[9.5,9.75]," +
                "\"precipitation\":[0.0,0.4]," +
                "\"wind_speed_10m\":[2.0,2.5]}";

        List<QuarterHourlyForecast> list = extractor.extractQuarterHourlyForecast(payload);

        assertNotNull(list);
        assertEquals(2, list.size());
        assertTrue(list.get(1).getPrecipitation() > 0);
        assertEquals(9.75f, list.get(1).getTemperature(), 0.001f);
    }
}
