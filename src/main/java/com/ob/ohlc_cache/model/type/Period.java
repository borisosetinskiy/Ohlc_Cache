package com.ob.ohlc_cache.model.type;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
public enum Period {
    M1("1 minute", 60_000L, 1, 1, TimeUnit.DAYS, 3, 10000, TimeUnit.DAYS.toSeconds(5000), 10, 4320),
    M5("5 minutes", 60_000L * 5, 5, 2, TimeUnit.DAYS, 15, 10000, TimeUnit.DAYS.toSeconds(10000), 5, 4320),
    M10("10 minutes", 60_000L * 10, 10, 12, TimeUnit.DAYS, 30, 10000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    M15("15 minutes", 60_000L * 15, 15, 3, TimeUnit.DAYS, 45, 10000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    M30("30 minutes", 60_000L * 30, 30, 6, TimeUnit.DAYS, 90, 10000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    H1("1 hour", 3_600_000L, 60, 4, TimeUnit.DAYS, 180, 10000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    H2("2 hours", 3_600_000L * 2, 120, 7, TimeUnit.DAYS, 360, 10000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    H3("3 hours", 3_600_000L * 3, 180, 13, TimeUnit.DAYS, 540, 10000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    H4("4 hours", 3_600_000L * 4, 240, 8, TimeUnit.DAYS, 1644, 10000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    H8("8 hours", 3_600_000L * 8, 480, 11, TimeUnit.DAYS, 3285, 20000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    D1("1 day", 86_400_000L, 1440, 5, TimeUnit.DAYS, 10950, 20000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    D7("7 days", 86_400_000L * 7, 10080, 9, TimeUnit.DAYS, 10950, 20000, TimeUnit.DAYS.toSeconds(15000), 1, 4320),
    Mo1("1 month", 86_400_000L * 31, 43830, 10, TimeUnit.DAYS, 14640, 5000, TimeUnit.DAYS.toSeconds(15000), 1, 1220),
    y1("1 year", 86_400_000L * 366, 525960, 14, TimeUnit.DAYS, 14640, 2000, TimeUnit.DAYS.toSeconds(15000), 1, 100);

    static final Map<Integer, Period> byDurationId = new HashMap<>();

    static {
        for (Period period : values()) {
            byDurationId.put(period.durationId, period);
        }
    }


    final String description;
    final long timePeriod;
    final int minutes;
    final int durationId;
    final TimeUnit timeUnit;
    final int timeUnitCount;
    final long max;
    final long indexTimestamp;
    final long criticalDiff;
    final int perChunk;

    Period(String description
            , long timePeriod
            , int minutes
            , int durationId
            , TimeUnit timeUnit
            , int timeUnitCount
            , long max, long indexTimestamp, long criticalDiff, int perChunk) {
        this.description = description;
        this.timePeriod = timePeriod;
        this.minutes = minutes;
        this.durationId = durationId;
        this.timeUnit = timeUnit;
        this.timeUnitCount = timeUnitCount;
        this.max = max;
        this.indexTimestamp = indexTimestamp;
        this.criticalDiff = criticalDiff;
        this.perChunk = perChunk;
    }


    public static Period getByDurationId(int id) {
        return byDurationId.get(id);
    }


    public static boolean in(long t1, long t2, Period period) {
        return t1 <= t2 && (t1 + period.getTimePeriod() > t2);
    }

}
