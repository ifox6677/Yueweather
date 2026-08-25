package org.zhangjq0908.weather.weather_api.open_meteo;

import org.junit.Test;
import org.zhangjq0908.weather.weather_api.IApiToDatabaseConversion;
import org.zhangjq0908.weather.weather_api.IApiToDatabaseConversion.WeatherCategories;

import static org.junit.Assert.assertEquals;

public class OMToDatabaseConversionTest {

    private final OMToDatabaseConversion conversion = new OMToDatabaseConversion();

    private int numVal(WeatherCategories category) {
        return category.getNumVal();
    }

    @Test
    public void cloudCodes() {
        assertEquals(numVal(WeatherCategories.CLEAR_SKY), conversion.convertWeatherCategory("0"));
        assertEquals(numVal(WeatherCategories.FEW_CLOUDS), conversion.convertWeatherCategory("1"));
        assertEquals(numVal(WeatherCategories.SCATTERED_CLOUDS), conversion.convertWeatherCategory("2"));
        assertEquals(numVal(WeatherCategories.OVERCAST_CLOUDS), conversion.convertWeatherCategory("3"));
    }

    @Test
    public void fogBoundaries() {
        assertEquals(numVal(WeatherCategories.MIST), conversion.convertWeatherCategory("45"));
        assertEquals(numVal(WeatherCategories.MIST), conversion.convertWeatherCategory("48"));
    }

    @Test
    public void drizzleRanges() {
        assertEquals(numVal(WeatherCategories.DRIZZLE_RAIN), conversion.convertWeatherCategory("50"));
        assertEquals(numVal(WeatherCategories.DRIZZLE_RAIN), conversion.convertWeatherCategory("55"));
        assertEquals(numVal(WeatherCategories.FREEZING_DRIZZLE_RAIN), conversion.convertWeatherCategory("56"));
        assertEquals(numVal(WeatherCategories.FREEZING_DRIZZLE_RAIN), conversion.convertWeatherCategory("57"));
    }

    @Test
    public void rainRanges() {
        assertEquals(numVal(WeatherCategories.LIGHT_RAIN), conversion.convertWeatherCategory("60"));
        assertEquals(numVal(WeatherCategories.LIGHT_RAIN), conversion.convertWeatherCategory("61"));
        assertEquals(numVal(WeatherCategories.MODERATE_RAIN), conversion.convertWeatherCategory("62"));
        assertEquals(numVal(WeatherCategories.MODERATE_RAIN), conversion.convertWeatherCategory("63"));
        assertEquals(numVal(WeatherCategories.HEAVY_RAIN), conversion.convertWeatherCategory("64"));
        assertEquals(numVal(WeatherCategories.HEAVY_RAIN), conversion.convertWeatherCategory("65"));
        assertEquals(numVal(WeatherCategories.LIGHT_FREEZING_RAIN), conversion.convertWeatherCategory("66"));
        assertEquals(numVal(WeatherCategories.FREEZING_RAIN), conversion.convertWeatherCategory("67"));
    }

    @Test
    public void snowCodes() {
        assertEquals(numVal(WeatherCategories.LIGHT_SNOW), conversion.convertWeatherCategory("70"));
        assertEquals(numVal(WeatherCategories.LIGHT_SNOW), conversion.convertWeatherCategory("71"));
        assertEquals(numVal(WeatherCategories.LIGHT_SNOW), conversion.convertWeatherCategory("77"));  //snow grain
        assertEquals(numVal(WeatherCategories.MODERATE_SNOW), conversion.convertWeatherCategory("72"));
        assertEquals(numVal(WeatherCategories.MODERATE_SNOW), conversion.convertWeatherCategory("73"));
        assertEquals(numVal(WeatherCategories.HEAVY_SNOW), conversion.convertWeatherCategory("74"));
        assertEquals(numVal(WeatherCategories.HEAVY_SNOW), conversion.convertWeatherCategory("75"));
    }

    @Test
    public void showerAndThunderstormCodes() {
        assertEquals(numVal(WeatherCategories.LIGHT_SHOWER_RAIN), conversion.convertWeatherCategory("80"));
        assertEquals(numVal(WeatherCategories.SHOWER_RAIN), conversion.convertWeatherCategory("81"));
        assertEquals(numVal(WeatherCategories.SHOWER_RAIN), conversion.convertWeatherCategory("82"));
        assertEquals(numVal(WeatherCategories.LIGHT_SHOWER_SNOW), conversion.convertWeatherCategory("85"));
        assertEquals(numVal(WeatherCategories.SHOWER_SNOW), conversion.convertWeatherCategory("86"));
        assertEquals(numVal(WeatherCategories.THUNDERSTORM), conversion.convertWeatherCategory("95"));
        assertEquals(numVal(WeatherCategories.THUNDERSTORM_HAIL), conversion.convertWeatherCategory("96"));
        assertEquals(numVal(WeatherCategories.THUNDERSTORM_HAIL), conversion.convertWeatherCategory("99"));
    }

    @Test
    public void unknownCodeFallsBackToError() {
        assertEquals(numVal(WeatherCategories.ERROR), conversion.convertWeatherCategory("4"));
        assertEquals(numVal(WeatherCategories.ERROR), conversion.convertWeatherCategory("44"));
        assertEquals(numVal(WeatherCategories.ERROR), conversion.convertWeatherCategory("68"));
        assertEquals(numVal(WeatherCategories.ERROR), conversion.convertWeatherCategory("76"));
        assertEquals(numVal(WeatherCategories.ERROR), conversion.convertWeatherCategory("87"));
        assertEquals(numVal(WeatherCategories.ERROR), conversion.convertWeatherCategory("-1"));
        assertEquals(numVal(WeatherCategories.ERROR), conversion.convertWeatherCategory("100"));
    }
}
