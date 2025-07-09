package com.ob.ohlc_cache.service.chronicle;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.ob.ohlc_cache.model.cache.IndexKey;
import com.ob.ohlc_cache.model.cache.IndexTask;
import com.ob.ohlc_cache.model.cache.IndexValue;
import com.ob.ohlc_cache.model.type.Direction;
import com.ob.ohlc_cache.util.IndexKeyUtil;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ExternalMapQueryContext;
import net.openhft.chronicle.map.MapEntry;
import net.openhft.chronicle.values.Values;
import org.jctools.queues.MpscArrayQueue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;

@Qualifier("indexApi")
@Service
@Slf4j
@ConditionalOnProperty(prefix = "cache.index", name = "name", havingValue = "chronicle")
public class ChronicleIndexApi implements IndexApi {

    private final ChronicleMap<IndexKey, IndexValue> seriesIndex;
    private final Queue<IndexTask> indexQueue;
    private final Thread indexerThread;
    private volatile boolean running = true;
    Cache<String, IndexValue> chunkValues = CacheBuilder.newBuilder()
            .expireAfterAccess(24, TimeUnit.HOURS)
            .removalListener((RemovalListener<String, IndexValue>) notification -> {
                try {
                    assert notification.getValue() != null;
                    notification.getValue().close();
                } catch (Exception ignored) {
                }
            })// Удаляем неиспользуемые чанки через 24 часа
            .build();
    final int indexChunkCapacity = 1000; // Максимальное количество таймстемпов в одном чанке

    IndexValue value() {
        var key = "T_" + Thread.currentThread().threadId();
        try {
            return chunkValues.get(key, IndexValue::new);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create IndexValue", e);
        }
    }


    public ChronicleIndexApi(  @Value("${cache.index.time-series.queue-size:131072}")Integer indexQueueSize,
                               @Value("${cache.index.time-series.base-path}") String indexPath) throws IOException {

        File indexFile = new File(indexPath, "series_index.dat");
        if (indexFile.getParentFile() != null) {
            indexFile.getParentFile().mkdirs();
        }

        this.seriesIndex = ChronicleMap.of(IndexKey.class, IndexValue.class)
                .name("series-timestamps-index")
                .entries(20000)
                .averageValueSize(IndexValue.getPageSize(indexChunkCapacity))
                .createPersistedTo(indexFile);

        this.indexQueue = new MpscArrayQueue<>(indexQueueSize);
        this.indexerThread = new Thread(this::runIndexer, "chronicle-indexer-thread");
        this.indexerThread.setDaemon(true);
        this.indexerThread.start();
    }

    @Override
    public void close() {
        log.info("Closing Chronicle Index... Shutting down indexer thread.");
        running = false;
        try {
            indexerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        chunkValues.asMap().values().forEach(IndexValue::close);
        if (seriesIndex != null && !seriesIndex.isClosed()) seriesIndex.close();
        log.info("Chronicle Index closed.");
    }

    @Override
    public void add(String seriesId, Long timestamp, long durationTimestamp) {
        if (!indexQueue.offer(new IndexTask(seriesId, timestamp, durationTimestamp))) {
            log.error("Chronicle index queue is full! Index update for chunk {} will be missed.", timestamp);
        }
    }

    private void runIndexer() {
        log.info("Chronicle indexer thread started.");
        final List<IndexTask> drainedTasks = new ArrayList<>(1024);
        final IndexKey indexKey = Values.newHeapInstance(IndexKey.class);
        try (IndexValue indexManager = new IndexValue()) {
            indexManager.init(indexChunkCapacity);
            while (running || !indexQueue.isEmpty()) {
                try {
                    IndexTask tempTask;
                    while ((tempTask = indexQueue.poll()) != null) {
                        drainedTasks.add(tempTask);
                        log.info("Drained task: seriesId={}, chunkTimestamp={}, indexGroupDurationSec={}",
                                tempTask.seriesId(), tempTask.chunkTimestamp(), tempTask.indexGroupDurationSec());
                    }
                    if (!drainedTasks.isEmpty()) {
                        Map<IndexKey, List<Long>> updatesByGroup = drainedTasks.stream()
                                .collect(Collectors.groupingBy(
                                        task -> {
                                            indexKey.setSeriesId(task.seriesId());
                                            indexKey.setIndexGroupTimestamp(getPureTimestamp(task.chunkTimestamp(), task.indexGroupDurationSec()));
                                            return IndexKeyUtil.copy(indexKey);
                                        },
                                        Collectors.mapping(IndexTask::chunkTimestamp, Collectors.toList())
                                ));

                        Deque<Map.Entry<IndexKey, List<Long>>> retryQueue = new ArrayDeque<>(updatesByGroup.entrySet());

                        Map.Entry<IndexKey, List<Long>> entryIndex;
                        while ((entryIndex = retryQueue.poll()) != null) {

                            IndexKey currentKey = entryIndex.getKey();
                            List<Long> newTimestamps = entryIndex.getValue();
                            try (ExternalMapQueryContext<IndexKey, IndexValue, ?> context =
                                         seriesIndex.queryContext(currentKey)) {
                                Lock lock = context.updateLock();
                                boolean isLocked = false;
                                try {
                                    isLocked = lock.tryLock(200, TimeUnit.MILLISECONDS);
                                    if (isLocked) {
                                        final MapEntry<IndexKey, IndexValue> entry = context.entry();
                                        indexManager.clear();
                                        if (entry == null) {
                                            for (Long ts : newTimestamps) {
                                                indexManager.insert(ts);
                                            }
                                            context.insert(Objects.requireNonNull(context.absentEntry()),context.wrapValueAsData(indexManager));

                                        } else {
                                            entry.value().getUsing(indexManager);
                                            for (Long ts : newTimestamps) {
                                                indexManager.insert(ts);
                                            }
                                            entry.doReplaceValue(context.wrapValueAsData(indexManager));
                                        }
                                    } else {
                                        retryQueue.offerLast(entryIndex);
                                        log.warn("Could not acquire lock for key {} within 500ms. High contention.", currentKey.getSeriesId());
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    log.error("Thread was interrupted while waiting for cache lock", e);
                                    throw new RuntimeException("Operation was cancelled", e);
                                } finally {
                                    if (isLocked) {
                                        lock.unlock();
                                        log.info("Unlocked lock for key {}", currentKey.getSeriesId());
                                    }
                                }
                            }
                        }
                        drainedTasks.clear();
                    } else {
                        if (!running) break;
                        sleep(10);
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    log.error("Exception in indexer thread", e);
                }
            }
            log.info("Chronicle indexer thread finished.");
        }

    }

    @Override
    public List<Long> fetchChunkTimestamps(String seriesId,
                                           long startTimestamp,
                                           int count,
                                           Direction direction,
                                           long chunkDurationSec) {

        long indexGroupTs = getPureTimestamp(startTimestamp, chunkDurationSec);

        final IndexKey indexKey = Values.newHeapInstance(IndexKey.class);
        indexKey.setSeriesId(seriesId);
        indexKey.setIndexGroupTimestamp(indexGroupTs);

        IndexValue usingValue = value();
        // 1. ОДИН вызов get() для получения всего индекса за период
        var exist = seriesIndex.getUsing(indexKey, usingValue);
        if (exist == null) {
            return Collections.emptyList();
        }


        // 4. Выполняем быстрый бинарный поиск по массиву в памяти, чтобы найти стартовую позицию
        int searchResult = usingValue.binarySearch(startTimestamp);


        int startIndex;
        if (direction == Direction.FORWARD) {
            // Если точное совпадение, начинаем с него. Если нет - с точки вставки.
            startIndex = (searchResult >= 0) ? searchResult : -searchResult - 1;
        } else { // BACKWARD
            // Если точное совпадение, начинаем с него. Если нет - с элемента перед точкой вставки.
            startIndex = (searchResult >= 0) ? searchResult : -searchResult - 2;
        }

        if(startIndex < 0) return Collections.emptyList();
        // 5. Формируем и возвращаем срез (slice) из нужных ключей чанков
        final List<Long> result = new ArrayList<>(count);
        if (direction == Direction.FORWARD) {
            for (int i = startIndex; i < usingValue.getCount() && result.size() < count; i++) {
                // Проверка, чтобы не выйти за границы, если binarySearch вернул 0
                if (i < 0) continue;
                result.add(usingValue.getTimestampByIndex(i));
            }
        } else { // BACKWARD
            for (int i = startIndex; i >= 0 && result.size() < count; i--) {
                result.add(usingValue.getTimestampByIndex(i));
            }
        }

        return result;
    }

    private long getPureTimestamp(long ts, long dur) {
        return ts - (ts % dur);
    }
}
