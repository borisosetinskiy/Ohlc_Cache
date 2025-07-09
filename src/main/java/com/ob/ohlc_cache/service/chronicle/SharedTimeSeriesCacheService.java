package com.ob.ohlc_cache.service.chronicle;

import com.ob.ohlc_cache.model.Candle;
import com.ob.ohlc_cache.model.Chart;
import com.ob.ohlc_cache.model.type.Direction;
import com.ob.ohlc_cache.model.type.MarketType;
import com.ob.ohlc_cache.model.type.Period;
import com.ob.ohlc_cache.model.type.SearchMode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@Data
public class SharedTimeSeriesCacheService {
    final IndexApi indexApi;
    final CandleValidator candleValidator;

    @Value("${cache.diable:true}")
    Boolean cacheDiable;

    @Value("${cache.chronicle.time-series.max-records:10000000}") // Default max records
    private Long maxRecords;
    @Value("${cache.chronicle.time-series.base-path}")
    String chroniclePath;


    final Map<String, CandleTimeSeriesCache> caches = new ConcurrentHashMap<>();


    @PostConstruct
    public void init() throws Exception {
        for (MarketType marketType : MarketType.values()) {
            for (Period period : Period.values()) {
                CandleTimeSeriesCache cache = new CandleTimeSeriesCache(
                        marketType
                        , period
                        , period.getMax()
                        , chroniclePath
                        , indexApi
                        , candleValidator);
                caches.put(key(marketType, period), cache);
            }
        }
    }

    public String key(MarketType marketType, Period period) {
        return marketType.name() + ":" + period.name();
    }

    /**
     * Получает кэш для указанного типа рынка и периода.
     * @param marketType тип рынка
     * @param period период
     * @return кэш или null если не найден
     */
    public CandleTimeSeriesCache getCache(MarketType marketType, Period period) {
        return caches.get(key(marketType, period));
    }

    /**
     * Получает все кэши.
     * @return Map всех кэшей
     */
    public Map<String, CandleTimeSeriesCache> getAllCaches() {
        return new ConcurrentHashMap<>(caches);
    }

    @PreDestroy
    public void close() {
        caches.values().forEach(chunkCache -> {
            if (chunkCache.isClosed()) chunkCache.close();
        });

        log.info("Shared Cache Service closed.");
    }

    public void put(Chart chart) {
        if (cacheDiable)
            return;
        insertAll(chart.getSymbol(),
                chart.getMarketType(),
                chart.getPeriod(),
                chart.getCandles(),
                chart.getDbOffset(),
                chart.getDbCount());
    }

    public void insertAll(String symbol
            , MarketType marketType
            , Period period
            , Candle[] candles
            , int offset, int count) {
        if (cacheDiable)
            return;
        var key = key(marketType, period);
        CandleTimeSeriesCache cache = caches.get(key);
        if (cache == null) {
            log.error("Cache for {} not found. Available caches: {}", key, caches.keySet());
            return;
        }
        if (candles == null || candles.length == 0) {
            log.warn("No candles to insert for symbol: {}", symbol);
            return;
        }
        if (offset < 0 || offset >= candles.length) {
            log.error("Invalid offset {} for candles array of size {}", offset, candles.length);
            return;
        }
        if (count <= 0 || offset + count > candles.length) {
            log.error("Invalid count {} with offset {} for candles array of size {}", count, offset, candles.length);
            return;
        }
        cache.batchInsert(symbol, period.getDurationId(), candles, offset, count);
    }

    public void insert(String symbol
            , MarketType marketType
            , Period period
            , long timestamp
            , double o
            , double h
            , double l
            , double c
            , double vol
            , int tick) {
        if (cacheDiable)
            return;
        var key = key(marketType, period);
        CandleTimeSeriesCache cache = caches.get(key);
        if (cache == null) {
            log.error("Cache for {} not found. Available caches: {}", key, caches.keySet());
            return;
        }
        cache.insert(symbol, period.getDurationId(), timestamp, o, h, l, c, vol, tick);
    }


    public void get(Chart chart) {

        if (cacheDiable)
            return;
        var key = key(chart.getMarketType(), chart.getPeriod());
        CandleTimeSeriesCache cache = caches.get(key);
        if (cache == null) {
            log.error("Cache for {} not found. Available caches: {}", key, caches.keySet());
            return;
        }
        int count = chart.getCacheCount() - chart.getFilledSize();
        Candle[] candles = chart.getCandles();
        SearchMode searchMode = SearchMode.NEAREST_AVAILABLE;
        int filled = cache.batchRead(candles, chart.getSymbol()
                , chart.getMarketType()
                , chart.getPeriod().getDurationId()
                , chart.getCurrentCandleTime()
                , chart.getCacheOffset()
                , count, Direction.BACKWARD, searchMode);
        if (filled > 0) {
            chart.setCacheOffset(0);
            chart.setFilledCacheSize(filled);
            chart.setFilledSize(chart.getFilledSize() + filled);
            long time = candles[chart.getFilledSize() - 1].getTime();
            if (time > 0) {
                chart.setCurrentCandleTime(time);
            }
        }
    }

}
