package com.ob.ohlc_cache.service.chronicle;


import com.ob.ohlc_cache.model.Candle;
import com.ob.ohlc_cache.model.cache.CandleChunkValue;
import com.ob.ohlc_cache.model.cache.ChunkKey;
import com.ob.ohlc_cache.model.type.Direction;
import com.ob.ohlc_cache.model.type.MarketType;
import com.ob.ohlc_cache.model.type.Period;
import com.ob.ohlc_cache.model.type.SearchMode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ExternalMapQueryContext;
import net.openhft.chronicle.map.MapEntry;
import net.openhft.chronicle.values.Values;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class CandleTimeSeriesCache implements AutoCloseable {
    private final Integer candlesPerChunk;
    private final IndexApi indexApi;
    private final Period period;
    private final CandleValidator candleValidator;
    @Getter
    private final ChronicleMap<ChunkKey, CandleChunkValue> chunkCache;
    final Map<String, Lock> seriesLocks = new ConcurrentHashMap<>();
    final Map<String, CandleChunkValue> chunkValues = new ConcurrentHashMap<>();
    long chunkDurationSec;

    CandleChunkValue getChunk() {
        var key = Thread.currentThread().threadId(); // ⚠️ желательно, чтобы thread name был уникален и не менялся
        return chunkValues.computeIfAbsent("T_" + key, s -> new CandleChunkValue());
    }

    public CandleTimeSeriesCache(MarketType marketType
            , Period period
            , Long maxRecords
            , String chroniclePath
            , IndexApi indexApi
            , CandleValidator candleValidator) throws Exception {
        this.candlesPerChunk = period.getPerChunk();
        this.indexApi = indexApi;
        this.period = period;
        this.candleValidator = candleValidator;


        log.info("Initializing Shared Time-Series Cache Service {}...", marketType + "_" + period);

        File cacheFile = new File(chroniclePath, marketType + "_" + period + "_shared_chunk_cache.dat");
        File parentDir = cacheFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (parentDir.mkdirs()) {
                log.info("Created cache directory: {}", parentDir.getAbsolutePath());
            } else {
                log.error("Failed to create cache directory: {}", parentDir.getAbsolutePath());
            }
        }

        long pageSize = CandleChunkValue.getPageSize(candlesPerChunk);
        this.chunkCache = ChronicleMap.of(ChunkKey.class, CandleChunkValue.class)
                .name(marketType + "-" + period + "-shared-chunk-cache")
                .entries(maxRecords)
                .actualSegments(32)
                .averageValueSize(pageSize)
                .createPersistedTo(cacheFile);

        TimeUnit timeUnit = period.getTimeUnit();
        long timeUnitCount = period.getTimeUnitCount();

        this.chunkDurationSec = timeUnit.toSeconds(timeUnitCount);
    }

    @Override
    public void close() {
        try {
            if (chunkCache != null && !chunkCache.isClosed()) {
                chunkCache.close();
            }
        } catch (Exception ignore) {
        }
        for (CandleChunkValue chunk : chunkValues.values()) {
            chunk.close();
        }
        chunkValues.clear();
    }

    public boolean isClosed() {
        return chunkCache == null || chunkCache.isClosed();
    }


    public void insert(String symbol, long duration, long timestamp, double o, double h, double l, double c, double vol, int tick) {
        long chunkTs = getChunkStartTimestamp(timestamp);
        String seriesId = getSeriesId(symbol, duration);

        ChunkKey key = ChunkKey.of(symbol, duration, chunkTs);

        try (ExternalMapQueryContext<ChunkKey, CandleChunkValue, ?> context =
                     chunkCache.queryContext(key)) {
            Lock lock = context.updateLock();
            boolean isLocked = false;
            try {
                isLocked = lock.tryLock(200, TimeUnit.MILLISECONDS);
                if (isLocked) {
                    final MapEntry<ChunkKey, CandleChunkValue> entry = context.entry();
                    CandleChunkValue usingValue = getChunk();
                    if (entry == null) {
                        usingValue.init(candlesPerChunk);
                        indexApi.add(seriesId, key.getChunkStartTimestamp(), period.getIndexTimestamp());
                        usingValue.insert(timestamp, o, h, l, c, vol, tick);
                        context.insert(Objects.requireNonNull(context.absentEntry()), context.wrapValueAsData(usingValue));
                    } else {
                        entry.value().getUsing(usingValue);
                        if (usingValue.insert(timestamp, o, h, l, c, vol, tick)) {
                            entry.doReplaceValue(context.wrapValueAsData(usingValue));
                        }
                    }
                } else {
                    log.warn("Could not acquire lock for key {} within 500ms. High contention.", key);
                    throw new IllegalStateException("Cache is under high load, please try again later.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread was interrupted while waiting for cache lock", e);
                throw new RuntimeException("Operation was cancelled", e);
            } finally {
                if (isLocked) {
                    lock.unlock();
                }
            }
        }
    }

    public void batchInsert(String symbol, long duration, Candle[] candles, int offset, int count) {
        if (candles == null || count == 0 || offset < 0 || count < 0 || offset + count > candles.length) {
            log.warn("Invalid arguments for batchInsert, ignoring. Offset: {}, Count: {}, Array Length: {}", offset, count, candles.length);
            return;
        }

        final String seriesId = getSeriesId(symbol, duration);

        final Map<Long, List<Candle>> candlesByChunk = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            Candle candle = candles[offset + i];
            if (candle == null) continue;
            long chunkTs = getChunkStartTimestamp(candle.getTime());
            candlesByChunk.computeIfAbsent(chunkTs, ts -> new ArrayList<>()).add(candle);
        }
        final Lock lock = seriesLocks.computeIfAbsent(seriesId, k -> new ReentrantLock());
        lock.lock();
        try {
            final ChunkKey key = Values.newHeapInstance(ChunkKey.class);
            for (Map.Entry<Long, List<Candle>> groupedEntry : candlesByChunk.entrySet()) {
                long chunkTs = groupedEntry.getKey();
                List<Candle> candlesForThisChunk = groupedEntry.getValue();
                key.setSymbol(symbol);
                key.setDuration(duration);
                key.setChunkStartTimestamp(chunkTs);

                CandleChunkValue usingValue = getChunk();
                var result = chunkCache.getUsing(key, usingValue);
                boolean isNewChunkPart = (result == null);
                if (isNewChunkPart) {
                    usingValue.init(candlesPerChunk);
                    indexApi.add(seriesId, chunkTs, period.getIndexTimestamp());
                }
                boolean isInserted = false;
                for (int i = candlesForThisChunk.size() - 1; i >= 0; i--) {
                    Candle candle = candlesForThisChunk.get(i);
                    if (!usingValue.insert(candle.getTime(), candle.getOpen(), candle.getHigh(),
                            candle.getLow(), candle.getClose(), candle.getVolume(), candle.getTick())) {
                        break;
                    }
                    if (!isInserted)
                        isInserted = true;
                }
                if (isInserted) {
                    chunkCache.put(key, usingValue);
                    log.info("Inserted {} candles into chunk starting at {} for symbol: {}, duration: {}, count {}",
                            candlesForThisChunk.size(), chunkTs, symbol, duration, usingValue.getCount());
                }

            }
        } finally {
            lock.unlock(); // Гарантированно освобождаем блокировку
        }
    }


    private long getChunkStartTimestamp(long ts) {
        return ts - (ts % chunkDurationSec);
    }

    private String getSeriesId(String s, long d) {
        return s + ":" + d;
    }

    public int batchRead(Candle[] candles, String symbol, MarketType marketType, long duration, long searchTimestamp, int offset, int count, Direction direction, SearchMode mode) {
        if (candles == null || candles.length == 0 || offset < 0 || count <= 0) {
            return 0;
        }
        final int actualCountToRead = Math.min(count, candles.length - offset);
        if (actualCountToRead <= 0) {
            return 0;
        }
        long start = System.nanoTime();
        try (var reader = new BatchReader(symbol, marketType, duration, searchTimestamp, actualCountToRead, direction)) {
            long end = System.nanoTime();
            log.info("BatchReader initialized in {} ms for symbol: {}, duration: {}, searchTimestamp: {}, count: {}, direction: {}, mode: {}",
                    TimeUnit.NANOSECONDS.toMillis(end - start), symbol, duration, searchTimestamp, actualCountToRead, direction, mode);
            return reader.read(candles, offset, searchTimestamp, mode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private class BatchReader implements AutoCloseable {
        private final String symbol;
        private final MarketType marketType;
        private final long duration;
        private final Direction direction;
        private final int requestedCount;

        private final List<Long> chunkTimestampsToVisit;
        private final CandleChunkValue reusableChunk = new CandleChunkValue();
        private final Candle reusableCandle = Values.newNativeReference(Candle.class);
        private final ChunkKey reusableKey = Values.newHeapInstance(ChunkKey.class);

        BatchReader(String symbol, MarketType marketType
                , long duration
                , long searchTimestamp, int count, Direction direction) {



            this.symbol = symbol;
            this.marketType = marketType;
            this.duration = duration;
            this.requestedCount = count;
            this.direction = direction;

            this.chunkTimestampsToVisit = prefetchChunkTimestamps(symbol, duration, searchTimestamp, count, direction);
        }

        int read(Candle[] destination, int offset, long searchTimestamp, SearchMode mode) {
            if (chunkTimestampsToVisit.isEmpty()) {
                return 0;
            }

            int candlesRead = 0;
            boolean firstChunk = true;

            for (int j = 0; j < chunkTimestampsToVisit.size() && candlesRead < requestedCount; j++) {
                long currentChunkTs = chunkTimestampsToVisit.get(j);
                if (!loadChunk(currentChunkTs)) {
                    continue; // Пропускаем, если чанк не найден (хотя не должно быть)
                }
                int chunkListIndex = j + 1;
                if (chunkListIndex < chunkTimestampsToVisit.size()) {
                    long nextChronologicalChunkTs = chunkTimestampsToVisit.get(chunkListIndex);

                    long expectedNextTs = (direction == Direction.FORWARD)
                            ? currentChunkTs + chunkDurationSec
                            : currentChunkTs - chunkDurationSec;

                    if (nextChronologicalChunkTs != expectedNextTs) {
                        log.debug("Data gap detected. Current chunk ts: {}, next chunk in index: {}. Stopping iteration.", currentChunkTs, nextChronologicalChunkTs);
                        return candlesRead;
                    }
                }

                int candlesInChunk = reusableChunk.getCount();
                int startIndexInChunk = (direction == Direction.FORWARD) ? 0 : candlesInChunk - 1;

                if (firstChunk) {
                    firstChunk = false;
                    int searchResult = reusableChunk.binarySearch(searchTimestamp);
                    if (mode == SearchMode.EXACT_START_TIME) {
                        if (searchResult < 0) return 0;
                        startIndexInChunk = searchResult;
                    } else {
                        if (searchResult >= 0) {
                            startIndexInChunk = searchResult;
                        } else {
                            int insertPoint = -searchResult - 1;
                            startIndexInChunk = (direction == Direction.FORWARD) ? insertPoint : insertPoint - 1;
                        }
                    }
                    if (startIndexInChunk < 0 || startIndexInChunk >= candlesInChunk) continue;
                }

                // Проверяем, что стартовый индекс валиден
                if (!isIndexInBounds(startIndexInChunk, candlesInChunk)) {
                    continue; // В этом чанке нет подходящих свечей, переходим к следующему
                }

                // Основной цикл чтения из текущего чанка
                if (direction == Direction.FORWARD) {
                    for (int i = startIndexInChunk; i < candlesInChunk && candlesRead < requestedCount; i++) {
                        reusableChunk.readCandle(i, reusableCandle);
                        if(validateAndPopulate(destination, offset, candlesRead)){
                            candlesRead++;
                        }
                    }
                } else { // BACKWARD
                    for (int i = startIndexInChunk; i >= 0 && candlesRead < requestedCount; i--) {
                        reusableChunk.readCandle(i, reusableCandle);
                        if(validateAndPopulate(destination, offset, candlesRead)){
                            candlesRead++;
                        }
                    }
                }

                // Если набрали достаточно, выходим из основного цикла
                if (candlesRead >= requestedCount) {
                    break;
                }
            }
            return candlesRead;
        }

        private boolean loadChunk(long chunkTs) {
            reusableKey.setSymbol(symbol);
            reusableKey.setDuration(duration);
            reusableKey.setChunkStartTimestamp(chunkTs);// part всегда 0, как вы и решили
            reusableChunk.clear();
            return chunkCache.getUsing(reusableKey, reusableChunk) != null;
        }

        private boolean isIndexInBounds(int index, int count) {
            return index >= 0 && index < count;
        }

        @Override
        public void close() throws Exception {
            try {
                Objects.requireNonNull(reusableCandle.bytesStore()).releaseLast();
            } catch (Exception ignored) {
            }
            try {
                reusableChunk.close();
            } catch (Exception ignored) {
            }
        }

        private boolean validateAndPopulate(Candle[] destination, int offset, int candlesRead){
            if (reusableCandle.getTime() > 0) {
                int currentIndex = offset + candlesRead;
                reusableCandle.copyReusable(destination[currentIndex]);
                int previousIndex = currentIndex - 1;
                if (previousIndex >= 0) {
                    return candleValidator.validateCandle(period
                            , marketType
                            , destination[currentIndex]
                            , destination[previousIndex]);
                }
                return true;
            }
            return false;
        }
    }



    private List<Long> prefetchChunkTimestamps(String symbol, long duration, long searchTimestamp, int candleCount, Direction direction) {
        final String seriesId = getSeriesId(symbol, duration);

        final int chunksToFetch = ((candleCount + candlesPerChunk - 1) / candlesPerChunk) + 5;

        return indexApi.fetchChunkTimestamps(seriesId, getChunkStartTimestamp(searchTimestamp)
                , chunksToFetch, direction, period.getIndexTimestamp());
    }


}
