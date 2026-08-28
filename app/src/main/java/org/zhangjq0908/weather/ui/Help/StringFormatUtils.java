package org.zhangjq0908.weather.ui.Help;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.icu.util.ChineseCalendar;
import android.icu.util.ULocale;
import androidx.preference.PreferenceManager;

import androidx.core.content.res.ResourcesCompat;
import org.zhangjq0908.weather.R;
import org.zhangjq0908.weather.preferences.AppPreferencesManager;

import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static java.lang.Boolean.TRUE;

public final class StringFormatUtils {

    private static final DecimalFormat decimalFormat = new DecimalFormat("0.0");
    private static final DecimalFormat intFormat = new DecimalFormat("0");

    public static String formatDecimal(float decimal) {
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
        return removeMinusIfZerosOnly(decimalFormat.format(decimal));
    }

    public static String formatInt(float decimal) {
        intFormat.setRoundingMode(RoundingMode.HALF_UP);
        return removeMinusIfZerosOnly(intFormat.format(decimal));
    }

    public static String formatInt(float decimal, String appendix) {
        return String.format("%s\u200a%s", removeMinusIfZerosOnly(formatInt(decimal)), appendix); //\u200a adds tiny space
    }

    public static String formatDecimal(float decimal, String appendix) {
        return String.format("%s\u200a%s", removeMinusIfZerosOnly(formatDecimal(decimal)), appendix);
    }

    public static String formatDecimalTemperature(Context context, float decimal, String appendix) {
        SharedPreferences sharedPreferences= PreferenceManager.getDefaultSharedPreferences(context);
        if(sharedPreferences.getBoolean("pref_TempDecimals", false)){
            return String.format("%s\u200a%s", formatDecimal(decimal), appendix);
        }else{
            return String.format("%s\u200a%s", formatInt(decimal), appendix);
        }
    }

    public static String formatTemperature(Context context, float temperature) {
        AppPreferencesManager prefManager = new AppPreferencesManager(PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()));
        return formatDecimalTemperature(context, prefManager.convertTemperatureFromCelsius(temperature), prefManager.getTemperatureUnit());
    }

    public static String formatPrecipitation(Context context, float precipitation) {
        SharedPreferences sharedPreferences= PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences.getString("precipitationUnit", "1").equals("1")) {  // mm
            if (precipitation < 10.0f) return formatDecimal(precipitation, context.getString(R.string.units_mm)); //show decimal only below 10mm
            else return formatInt(precipitation,context.getString(R.string.units_mm));
        } else {
            DecimalFormat inchFormatter = new DecimalFormat("0.00");
            inchFormatter.setRoundingMode(RoundingMode.HALF_UP);
            return String.format("%s\u200a%s", removeMinusIfZerosOnly(inchFormatter.format(precipitation / 25.4)), context.getString(R.string.units_in));
        }
    }

    public static String formatTimeWithoutZone(Context context, long time) {
        SharedPreferences sharedPreferences= PreferenceManager.getDefaultSharedPreferences(context);
        SimpleDateFormat df;
        if (android.text.format.DateFormat.is24HourFormat(context) || sharedPreferences.getBoolean("pref_TimeFormat", true)==TRUE){
            df = new SimpleDateFormat("HH:mm", Locale.getDefault());
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
        } else {
            df = new SimpleDateFormat("hh:mm aa", Locale.getDefault());
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        return df.format(time);
    }

    public static String formatDate(long time) {
        java.text.DateFormat df = java.text.DateFormat.getDateInstance(DateFormat.SHORT);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        return df.format(time);
    }

    private static final String[] LUNAR_MONTHS = {"正月","二月","三月","四月","五月","六月","七月","八月","九月","十月","冬月","腊月"};
    private static final String[] LUNAR_DAYS = {"初一","初二","初三","初四","初五","初六","初七","初八","初九","初十",
            "十一","十二","十三","十四","十五","十六","十七","十八","十九","二十",
            "廿一","廿二","廿三","廿四","廿五","廿六","廿七","廿八","廿九","三十"};

    /**
     * Formats the given time (already shifted to the target time zone, interpreted in GMT,
     * following the widget time convention) as Chinese lunar date, e.g. "七月十六" or "闰四月初五".
     */
    public static String formatLunarDate(long time) {
        ChineseCalendar chinese = new ChineseCalendar(android.icu.util.TimeZone.getTimeZone("GMT"), ULocale.CHINESE);
        chinese.setTimeInMillis(time);
        int month = chinese.get(ChineseCalendar.MONTH);               // 0-based
        int day = chinese.get(ChineseCalendar.DAY_OF_MONTH) - 1;      // array index
        boolean isLeapMonth = chinese.get(ChineseCalendar.IS_LEAP_MONTH) == 1;
        if (month < 0 || month >= LUNAR_MONTHS.length || day < 0 || day >= LUNAR_DAYS.length) return "";
        return (isLeapMonth ? "闰" : "") + LUNAR_MONTHS[month] + LUNAR_DAYS[day];
    }

    public static String formatWindSpeed(Context context, float wind_speed) {
        SharedPreferences sharedPreferences= PreferenceManager.getDefaultSharedPreferences(context);
        String unitPref = sharedPreferences.getString("speedUnit", "3");
        switch (unitPref) {
            case "1":
                return formatInt((float) (wind_speed * 3.6), context.getString(R.string.units_km_h));
            case "2":
                return formatInt((float) (wind_speed * 2.236), context.getString(R.string.units_mph));
            case "3":
                if (wind_speed < 0.3) {
                    return formatInt(0, context.getString(R.string.units_Bft)); // Calm
                } else if (wind_speed < 1.5) {
                    return formatInt(1, context.getString(R.string.units_Bft)); // Light air
                } else if (wind_speed < 3.3) {
                    return formatInt(2, context.getString(R.string.units_Bft)); // Light breeze
                } else if (wind_speed < 5.5) {
                    return formatInt(3, context.getString(R.string.units_Bft)); // Gentle breeze
                } else if (wind_speed < 7.9) {
                    return formatInt(4, context.getString(R.string.units_Bft)); // Moderate breeze
                } else if (wind_speed < 10.7) {
                    return formatInt(5, context.getString(R.string.units_Bft)); // Fresh breeze
                } else if (wind_speed < 13.8) {
                    return formatInt(6, context.getString(R.string.units_Bft)); // Strong breeze
                } else if (wind_speed < 17.1) {
                    return formatInt(7, context.getString(R.string.units_Bft)); // High wind
                } else if (wind_speed < 20.7) {
                    return formatInt(8, context.getString(R.string.units_Bft)); // Gale
                } else if (wind_speed < 24.4) {
                    return formatInt(9, context.getString(R.string.units_Bft)); // Strong gale
                } else if (wind_speed < 28.4) {
                    return formatInt(10, context.getString(R.string.units_Bft)); // Storm
                } else if (wind_speed < 32.6) {
                    return formatInt(11, context.getString(R.string.units_Bft)); // Violent storm
                } else {
                    return formatInt(12, context.getString(R.string.units_Bft)); // Hurricane
                }
            case "4":
                return formatInt((float) (wind_speed), context.getString(R.string.units_m_s));
            case "5":
                return formatInt((float) (wind_speed * 1.94384), context.getString(R.string.units_kn));
            default:
                return formatInt((float) (wind_speed * 2.236), context.getString(R.string.units_mph));
        }

    }

    public static Drawable colorWindSpeed(Context context, float wind_speed) {
        if (wind_speed < 0.3) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_transparent,null);
        } else if (wind_speed < 1.5) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_transparent,null);
        } else if (wind_speed < 3.3) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_transparent,null);
        } else if (wind_speed < 5.5) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_transparent,null);
        } else if (wind_speed < 7.9) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_transparent,null);
        } else if (wind_speed < 10.7) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_yellow,null);
        } else if (wind_speed < 13.8) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_yellow,null);
        } else if (wind_speed < 17.1) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_yellow,null);
        } else if (wind_speed < 20.7) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_orange,null);
        } else if (wind_speed < 24.4) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_orange,null);
        } else if (wind_speed < 28.4) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_lightred,null);
        } else if (wind_speed < 32.6) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_lightred,null);
        } else {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_red,null);
        }
    }

    public static int colorWindSpeedWidget(float wind_speed) {
        if (wind_speed < 0.3) {
            return R.drawable.ic_wind_empty;
        } else if (wind_speed < 1.5) {
            return R.drawable.ic_wind_empty;
        } else if (wind_speed < 3.3) {
            return R.drawable.ic_wind_empty;
        } else if (wind_speed < 5.5) {
            return R.drawable.ic_wind_empty;
        } else if (wind_speed < 7.9) {
            return R.drawable.ic_wind_empty;
        } else if (wind_speed < 10.7) {
            return R.drawable.ic_wind_yellow;
        } else if (wind_speed < 13.8) {
            return R.drawable.ic_wind_yellow;
        } else if (wind_speed < 17.1) {
            return R.drawable.ic_wind_yellow;
        } else if (wind_speed < 20.7) {
            return R.drawable.ic_wind_orange;
        } else if (wind_speed < 24.4) {
            return R.drawable.ic_wind_orange;
        } else if (wind_speed < 28.4) {
            return R.drawable.ic_wind_lightred;
        } else if (wind_speed < 32.6) {
            return R.drawable.ic_wind_lightred;
        } else {
            return R.drawable.ic_wind_lightred;
        }
    }

    public static Drawable colorUVindex(Context context, int uvindex) {
        if (uvindex <=2) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_transparent,null);
        } else if (uvindex <= 5) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_yellow,null);
        } else if (uvindex <= 7) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_orange,null);
        } else if (uvindex <= 10) {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_lightred,null);
        } else  {
            return ResourcesCompat.getDrawable(context.getResources(),R.drawable.rounded_violet,null);
        }
    }


    public static Integer widgetColorUVindex(Context context, int uvindex) {
        if (uvindex <=2) {
            return R.drawable.rounded_green;
        } else if (uvindex <= 5) {
            return R.drawable.rounded_yellow;
        } else if (uvindex <= 7) {
            return R.drawable.rounded_orange;
        } else if (uvindex <= 10) {
            return R.drawable.rounded_lightred;
        } else  {
            return R.drawable.rounded_violet;
        }
    }

    public static Integer getDayShort(int day){

        switch(day)    {
            case Calendar.MONDAY:
                day = R.string.abbreviation_monday;
                break;
            case Calendar.TUESDAY:
                day = R.string.abbreviation_tuesday;
                break;
            case Calendar.WEDNESDAY:
                day = R.string.abbreviation_wednesday;
                break;
            case Calendar.THURSDAY:
                day = R.string.abbreviation_thursday;
                break;
            case Calendar.FRIDAY:
                day = R.string.abbreviation_friday;
                break;
            case Calendar.SATURDAY:
                day = R.string.abbreviation_saturday;
                break;
            case Calendar.SUNDAY:
                day = R.string.abbreviation_sunday;
                break;
            default:
                day = R.string.abbreviation_monday;
        }
        return day;
    }

    public static Integer getDayLong(int day){

        switch(day)    {
            case Calendar.MONDAY:
                day = R.string.monday;
                break;
            case Calendar.TUESDAY:
                day = R.string.tuesday;
                break;
            case Calendar.WEDNESDAY:
                day = R.string.wednesday;
                break;
            case Calendar.THURSDAY:
                day = R.string.thursday;
                break;
            case Calendar.FRIDAY:
                day = R.string.friday;
                break;
            case Calendar.SATURDAY:
                day = R.string.saturday;
                break;
            case Calendar.SUNDAY:
                day = R.string.sunday;
                break;
            default:
                day = R.string.monday;
        }
        return day;
    }

    public static String removeMinusIfZerosOnly(String string){
        // It removes (replaces with "") the minus sign if it's followed by 0-n characters of "0.00000...",
        // so this will work for any similar result such as "-0", "-0." or "-0.000000000"
        // https://newbedev.com/negative-sign-in-case-of-zero-in-java
        return string.replaceAll("^-(?=0(\\.0*)?$)", "");
    }
}
