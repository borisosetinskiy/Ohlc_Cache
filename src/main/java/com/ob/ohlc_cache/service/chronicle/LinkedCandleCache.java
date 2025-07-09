package com.ob.ohlc_cache.service.chronicle;


import com.ob.ohlc_cache.model.Candle;
import com.ob.ohlc_cache.model.type.Period;

public interface LinkedCandleCache {
    void getCandles(Candle[] candles, int offset, String symbol, Period period, long endTime, int count);
    void updateCandle(String symbol, Period period, Candle candle);
    void removeCandle(String symbol, Period period, long time);
    int getTotalCandles(String symbol, Period period);
}
