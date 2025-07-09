package com.ob.ohlc_cache.model;


import com.ob.ohlc_cache.model.type.MarketType;
import com.ob.ohlc_cache.model.type.Period;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Chart {
    private String requestId;
    private Candle lastCandle;
    private Candle[] candles;
    private Long instrumentId;
    private String symbol;
    private Period period;
    private Long currentCandleTime = 0L;
    private Long requestStartTime;
    private Long requestEndTime;
    private long lastCandleTime;
    private Long startTime;
    private Long endTime;
    private Long searchEndTime;
    private Long endCacheTime;
    private Long endDbTime;
    private int count;
    private int filledSize;
    private int filledCacheSize;
    private int filledDbSize;
    private int cacheOffset;
    private int dbOffset;
    private int cacheCount;
    private int dbCount;
    private MarketType marketType;

    public Chart(int size) {
        lastCandle = CandleUtil.newHeapCandle();
        candles = CandleUtil.newHeapCandleArray(size);
    }


    public void clear() {
        this.requestId = null;
        this.symbol = null;
        this.instrumentId = 0L;
        this.period = null;
        this.requestStartTime = null;
        this.requestEndTime = null;
        this.startTime = null;
        this.endTime = null;
        this.currentCandleTime = 0L;
        this.filledSize = 0;
        this.filledCacheSize = 0;
        this.filledDbSize = 0;
        this.count = 0;
        this.lastCandleTime = 0;
        this.marketType = null;
        this.cacheOffset = 0;
        this.dbOffset = 0;
        this.cacheCount = 0;
        this.dbCount = 0;
        CandleUtil.clear(lastCandle);
        CandleUtil.clear(candles);
    }

    public void setEndTime(long endTime) {
        this.endTime = Math.min(System.currentTimeMillis(), endTime);
    }

    public void setSearchEndTime(long searchEndTime) {
        this.searchEndTime = Math.min(System.currentTimeMillis(), searchEndTime);
    }

    public void setCurrentCandleTime(long newValue) {
        if(this.currentCandleTime != null && newValue == this.currentCandleTime)
            return;
        log.info("Previous {} , New {} ", this.currentCandleTime, newValue);
        this.currentCandleTime = newValue;

    }

}
