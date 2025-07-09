package com.ob.ohlc_cache.service.chronicle;

import com.ob.ohlc_cache.model.Candle;
import com.ob.ohlc_cache.model.type.MarketType;
import com.ob.ohlc_cache.model.type.Period;
import com.ob.ohlc_cache.service.MarketTimeService;
import com.ob.ohlc_cache.util.TimeUtil;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Data
@Service
public class CandleValidator {
    final MarketTimeService marketTimeService;

    public boolean validateCandle(Period period
            , MarketType marketType
            , Candle currentCandle
            , Candle previousCandle) {
        if (currentCandle.getTime() <= 0 || previousCandle.getTime() <= 0)
            return false;
        var zoneId = marketTimeService.getZoneForMarket(marketType);
        ZonedDateTime currentDateTime = TimeUtil.fromSeconds(currentCandle.getTime(), zoneId);
        ZonedDateTime previousDateTime = TimeUtil.fromSeconds(previousCandle.getTime(), zoneId);
        int difference = TimeUtil.getTradingDifferenceCoefficient(previousDateTime
                , currentDateTime
                , period.getTimePeriod() / 1000L
                , zonedDateTime ->
                        marketTimeService.isMarketOpen(zonedDateTime, marketType)
        );
        if (difference > 0 && difference > period.getCriticalDiff()) {
            return false;
        }
        return true;
    }
}
