package com.ob.ohlc_cache.service;

import com.ob.ohlc_cache.model.type.MarketType;
import com.ob.ohlc_cache.model.type.Period;
import com.ob.ohlc_cache.util.TimeUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

@Data
@Service
@Slf4j
public class MarketTimeService {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId UTC = ZoneId.of("UTC");

    public ZoneId getZoneForMarket(MarketType market) {
        return switch (market) {
            case STOCK, FOREX -> NEW_YORK;
            case CRYPTO -> UTC;
        };
    }

    public boolean isMarketOpen(ZonedDateTime time, MarketType market) {
        DayOfWeek day = time.getDayOfWeek();
        LocalTime localTime = time.toLocalTime();

        return switch (market) {
            case STOCK -> day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                          && !localTime.isBefore(LocalTime.of(9, 30))
                          && !localTime.isAfter(LocalTime.of(16, 0));
            case FOREX -> {
                if (day == DayOfWeek.SATURDAY) yield false;
                if (day == DayOfWeek.SUNDAY) yield !localTime.isBefore(LocalTime.of(17, 0));
                if (day == DayOfWeek.FRIDAY) yield localTime.isBefore(LocalTime.of(17, 0));
                yield true;
            }
            case CRYPTO -> true;
        };
    }

    public ZonedDateTime getCandleStart(Long time, Period period, MarketType market) {
        return getCandleStart(TimeUtil.fromSeconds(time).atZone(getZoneForMarket(market)), period, market);
    }

    public ZonedDateTime getCandleStart(LocalDateTime time, Period period, MarketType market) {
        return getCandleStart(time.atZone(getZoneForMarket(market)), period, market);
    }

    public ZonedDateTime getCandleStart(ZonedDateTime time, Period period, MarketType market) {
        return switch (market) {
            case STOCK -> getStockCandleStart(time, period);
            case FOREX -> getForexCandleStart(time, period);
            case CRYPTO -> getCryptoCandleStart(time, period);
        };
    }

    private ZonedDateTime getStockCandleStart(ZonedDateTime time, Period period) {
        switch (period) {
            case Mo1 -> {
                return firstStockTradingDayOf(time.with(TemporalAdjusters.firstDayOfMonth()));
            }
            case y1 -> {
                return firstStockTradingDayOf(time.with(TemporalAdjusters.firstDayOfYear()));
            }
            case D7 -> {
                return time.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .withHour(9).withMinute(30).withSecond(0).withNano(0);
            }
            case D1, H8 -> {
                return time.truncatedTo(ChronoUnit.DAYS)
                        .withHour(9).withMinute(30).withSecond(0).withNano(0);
            }
            case M1 -> {
                long millis = time.toInstant().toEpochMilli();
                long rounded = millis - (millis % period.getTimePeriod());
                return Instant.ofEpochMilli(rounded).atZone(time.getZone());
            }
            default -> {
                ZonedDateTime marketOpen = time.truncatedTo(ChronoUnit.DAYS)
                        .withHour(9).withMinute(30).withSecond(0).withNano(0);
                if (!isMarketOpen(marketOpen, MarketType.STOCK)) {
                    if (marketOpen.getDayOfWeek() == DayOfWeek.SATURDAY) {
                        marketOpen = marketOpen.minusDays(1);
                    } else if (marketOpen.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        marketOpen = marketOpen.minusDays(2);
                    }
                }
                if (!isMarketOpen(time, MarketType.STOCK)) {
                    if (time.getDayOfWeek() == DayOfWeek.SATURDAY) {
                        time = time.withHour(15).withMinute(59).withSecond(0).withNano(0);
                        time = time.minusDays(1);
                    } else if (time.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        time = time.minusDays(2);
                        time = time.withHour(15).withMinute(59).withSecond(0).withNano(0);
                    } else {
                        if (time.getHour() > 16 || (time.getHour() == 16 && time.getMinute() > 0)) {
                            time = time.withHour(15).withMinute(59).withSecond(0).withNano(0);
                        }
                        if (time.getHour() < 9 || (time.getHour() == 9 && time.getMinute() < 30)) {
                            time = time.withHour(15).withMinute(59).withSecond(0).withNano(0);
                            time = time.minusDays(1);
                        }
                    }


                }
                long offsetMillis = Duration.between(marketOpen, time).toMillis();
                long factor = period.getTimePeriod();
                long steps = offsetMillis / factor;
                return marketOpen.plus(steps * factor, ChronoUnit.MILLIS);
            }
        }
    }

    private ZonedDateTime firstStockTradingDayOf(ZonedDateTime date) {
        ZonedDateTime candidate = date.withHour(9).withMinute(30).withSecond(0).withNano(0);
        while (!isMarketOpen(candidate, MarketType.STOCK)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private ZonedDateTime moveToPrevious(ZonedDateTime time, Period period, MarketType market, int count) {
        switch (period) {
            case y1 -> {
                return time.minusYears(count);
            }
            case Mo1 -> {
                return time.minusMonths(count);
            }
            case D7 -> {
                return time.minusWeeks(count);
            }
            case D1 -> {
                time = time.minusDays(count);
                if (market == MarketType.STOCK || market == MarketType.FOREX) {
                    while (!isMarketOpen(time, market)) {
                        time = time.minusDays(1);
                    }
                }
                return time;
            }
            default -> {
                if (market == MarketType.STOCK && period == Period.H8) {
                    time = time.minusDays(count);
                    while (!isMarketOpen(time, market)) {
                        time = time.minusDays(1);
                    }
                    return time;
                } else if (market == MarketType.FOREX || market == MarketType.STOCK) {
                    time = time.minus(period.getTimePeriod() * count, ChronoUnit.MILLIS);
                    while (!isMarketOpen(time, market)) {
                        time = time.minus(period.getTimePeriod(), ChronoUnit.MILLIS);
                    }
                    return time;
                }
                return time.minus(period.getTimePeriod() * count, ChronoUnit.MILLIS);
            }
        }
    }

    public ZonedDateTime moveToPreviousCandles(ZonedDateTime time, Period period, MarketType market, int count) {
        ZonedDateTime start = getCandleStart(time, period, market);
        return moveToPrevious(start, period, market, count);
    }

    public ZonedDateTime moveToPreviousCandleStart(ZonedDateTime time, Period period, MarketType market) {
        ZonedDateTime start = getCandleStart(time, period, market);
        return moveToPrevious(start, period, market, 1);
    }

    private ZonedDateTime getForexCandleStart(ZonedDateTime time, Period period) {
        switch (period) {
            case Mo1 -> {
                return firstForexTradingDayOf(time.with(TemporalAdjusters.firstDayOfMonth()));
            }
            case y1 -> {
                return firstForexTradingDayOf(time.with(TemporalAdjusters.firstDayOfYear()));
            }
            case D1 -> {
                return time.truncatedTo(ChronoUnit.DAYS)
                        .withHour(17).withMinute(0).withSecond(0).withNano(0);
            }
            case D7 -> {
                return time.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                        .withHour(17).withMinute(0).withSecond(0).withNano(0);
            }
            default -> {
                ZonedDateTime base = time.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                        .withHour(17).withMinute(0).withSecond(0).withNano(0);
                if (!isMarketOpen(time, MarketType.FOREX)) {
                    base = base.minusDays(7);

                    if (time.getDayOfWeek() == DayOfWeek.SUNDAY && time.getHour() < 17) {
                        time = time.minusDays(2);
                        time = time.withHour(17).withMinute(0).withSecond(0).withNano(0);
                    } else if (time.getDayOfWeek() == DayOfWeek.SATURDAY) {
                        time = time.minusDays(1);
                        time = time.withHour(17).withMinute(0).withSecond(0).withNano(0);
                    } else if (time.getDayOfWeek() == DayOfWeek.FRIDAY && time.getHour() >= 17) {
                        time = time.withHour(17).withMinute(0).withSecond(0).withNano(0);
                    }
                }
                long offset = Duration.between(base, time).toMillis();
                long factor = period.getTimePeriod(); // e.g., 2h = 7_200_000
                long steps = offset / factor;
                return base.plus(steps * factor, ChronoUnit.MILLIS);
            }
        }
    }

    private ZonedDateTime firstForexTradingDayOf(ZonedDateTime date) {
        ZonedDateTime candidate = date.withHour(17).withMinute(0).withSecond(0).withNano(0);
        while (!isMarketOpen(candidate, MarketType.FOREX)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }


    private ZonedDateTime getCryptoCandleStart(ZonedDateTime time, Period period) {
        switch (period) {
            case Mo1 -> {
                return time.with(TemporalAdjusters.firstDayOfMonth())
                        .truncatedTo(ChronoUnit.DAYS)
                        .withZoneSameInstant(UTC);
            }
            case y1 -> {
                return time.with(TemporalAdjusters.firstDayOfYear())
                        .truncatedTo(ChronoUnit.DAYS)
                        .withZoneSameInstant(UTC);
            }
            case D7 -> {
                return time.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                        .truncatedTo(ChronoUnit.DAYS)
                        .withZoneSameInstant(UTC);
            }
            default -> {
                ZonedDateTime base = time.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                        .truncatedTo(ChronoUnit.DAYS)
                        .withZoneSameInstant(UTC); // Sunday 00:00 UTC
                long offset = Duration.between(base, time).toMillis();
                long factor = period.getTimePeriod(); // in millis
                long steps = offset / factor;
                return base.plus(steps * factor, ChronoUnit.MILLIS);
            }
        }
    }

}
