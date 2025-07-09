package com.ob.ohlc_cache.util;

import com.ob.ohlc_cache.model.type.Period;
import lombok.experimental.UtilityClass;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

@UtilityClass
public class TimeUtil {

    final static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public static String toDateStringFromMills(long timestamp, ZoneId zoneId) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId);
        return localDateTime.format(FORMATTER);

    }

    public static String toDateStringFromSeconds(long timestamp, ZoneId zoneId) {
        var zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(timestamp), zoneId);
        return zonedDateTime.format(FORMATTER);
    }

    public static long countDaysToLoad(int size, Period period) {
        int days = (int) (size / (1440.0 / period.getMinutes())) + 1;
        return (days + 2 + (days / 5) * 2) * 86_400_000L;
    }

    public static double convertToDbEpochTime(double time, int offsetFromUTC) {
        return (time + offsetFromUTC * 3_600_000) / 86_400_000;
    }

    public static long searchClosestTime(long timeToFind, long[] arr) {

        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("Array must not be empty");
        }

        if (timeToFind <= arr[0]) {
            return arr[0];
        }
        if (timeToFind >= arr[arr.length - 1]) {
            return arr[arr.length - 1];
        }

        int lo = 0, hi = arr.length - 1;
        int pos = 0;
        while (lo <= hi && timeToFind >= arr[lo] && timeToFind <= arr[hi]) {
            long denominator = arr[hi] - arr[lo];
            pos = denominator == 0 ? lo
                    : (int) (lo + ((double) (hi - lo) / denominator) * (timeToFind - arr[lo]));

            if (arr[pos] == timeToFind)
                return pos;
            long value = arr[pos];
            if (value == timeToFind) {
                return value;
            }

            if (value < timeToFind) {
                lo = pos + 1;

            } else {
                hi = pos - 1;
            }
        }

        long loVal = arr[Math.min(lo, arr.length - 1)];
        long hiVal = arr[Math.max(hi, 0)];
        return Math.abs(timeToFind - loVal) <= Math.abs(timeToFind - hiVal) ? loVal : hiVal;
    }




    public static long nowSec() {
        return System.currentTimeMillis() / 1000;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    public static long toSec(ZonedDateTime zonedDateTime) {
        return zonedDateTime.toEpochSecond();
    }

    static final LocalDateTime startOfEpoch = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

    public static boolean isSecond(long timestamp) {
        // Convert assuming the timestamp is in seconds
        LocalDateTime dateTimeSeconds = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC);
        // Convert assuming the timestamp is in milliseconds
        LocalDateTime dateTimeMilliseconds = LocalDateTime.ofEpochSecond(timestamp / 1000, (int) (timestamp % 1000) * 1_000_000, ZoneOffset.UTC);

        LocalDateTime now = now();

        // Reasonable date range check (e.g., between 1970 and 10 years into the future)

        if (dateTimeSeconds.isAfter(startOfEpoch) && dateTimeSeconds.isBefore(now.plusYears(10))) {
            return true;
        } else if (dateTimeMilliseconds.isAfter(startOfEpoch) && dateTimeMilliseconds.isBefore(now.plusYears(10))) {
            return false;
        } else {
            // Both timestamps are out of reasonable range
            throw new IllegalArgumentException("The timestamp is not in a reasonable range.");
        }
    }

    public static LocalDateTime fromSeconds(Long timestamp) {
        return fromSeconds(timestamp, ZoneOffset.UTC);
    }

    public static LocalDateTime fromSeconds(Long timestamp, ZoneOffset zoneOffset) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofEpochSecond(timestamp, 0, zoneOffset);
    }

    public static ZonedDateTime fromSeconds(Long timestamp, ZoneId zoneId) {
        if (timestamp == null) {
            return null;
        }
        return Instant.ofEpochMilli(timestamp * 1000).atZone(zoneId);
    }


    public static LocalDateTime fromMillis(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofEpochSecond(timestamp / 1000, (int) (timestamp % 1000) * 1_000_000, ZoneOffset.UTC);
    }

    public static int getDifferenceCoefficient(long timestamp1, long timestamp2, long stepDurationMillis) {
        long diffMillis = timestamp1 - timestamp2;
        return (int) (diffMillis / stepDurationMillis);
    }

    public static int getTradingDifferenceCoefficient(ZonedDateTime searchTime
            , ZonedDateTime lastCandleTime
            , long stepDurationSec
            , Function<ZonedDateTime, Boolean> isTradingTime) {
        Duration stepDuration = Duration.ofSeconds(stepDurationSec);
        int count = 0;
        ZonedDateTime cursor = lastCandleTime;
        while (!cursor.isAfter(searchTime)) {
            if (isTradingTime.apply(cursor)) {
                count++;
            }
            cursor = cursor.plus(stepDuration);
        }
        return count;
    }

}


