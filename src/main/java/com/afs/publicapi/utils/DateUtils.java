package com.afs.publicapi.utils;

import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Slf4j
public class DateUtils {

    public static String formatDate(long t) {
        SimpleDateFormat sdfD = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdfD.format(t);
    }

    public static long getTimestamp(String time) {
        SimpleDateFormat sdfD = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            return sdfD.parse(time).getTime();
        } catch (ParseException e) {
            log.error("parse date error", e);
        }
        return 0l;
    }

    public static long getDayTimestamp(String time) {
        SimpleDateFormat sdfD = new SimpleDateFormat("yyyy-MM-dd");
        try {
            return sdfD.parse(time).getTime();
        } catch (ParseException e) {
            log.error("parse date error", e);
        }
        return 0l;
    }

    public static long getDaysTimestamp(long t) {
        SimpleDateFormat sdfD = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String day = getFormatDay(new Date(t));
        try {
            return sdfD.parse(day).getTime();
        } catch (ParseException e) {
            log.error("parse date error", e);
        }
        return 0l;
    }

    public static String getDaysForUser(long t, int num) {//
        return getFormatDay(new Date(t - num * (24 * 60 * 60 * 1000)));
    }

    public static long getDaysTimestamp(long t, int num) {//
        String timeStr = getDaysForUser(t, num);
        return getTimestamp(timeStr);
    }

    public static String getFormatDay(Date date) {
        SimpleDateFormat sdfD = new SimpleDateFormat("yyyy-MM-dd");
        String s = sdfD.format(date);
        return s + " 00:00:00";
    }

    public static String getDateStr(Date date) {
        SimpleDateFormat sdfD = new SimpleDateFormat("yyyy-MM-dd");
        String s = sdfD.format(date);
        return s;
    }

    public static long getTodayStartTimestamp() {
        Calendar calendar = Calendar.getInstance();

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        calendar.set(year, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTimeInMillis();
    }
}
