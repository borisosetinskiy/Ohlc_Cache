# Примеры использования - OHLC Cache

## Обзор

Данный документ содержит практические примеры использования системы OHLC Cache для различных сценариев.

## Базовые операции

### 1. Инициализация кэша

```java
@Service
public class OhlcCacheService {
    
    @Autowired
    private SharedTimeSeriesCacheService sharedTimeSeriesCacheService;
    
    // Инициализация с настройками
    public void initializeCache() {
        // Кэш автоматически инициализируется Spring Boot
        log.info("OHLC Cache initialized successfully");
    }
}
```

### 2. Вставка одиночной свечи

```java
public void insertSingleCandle() {
    try {
        sharedTimeSeriesCacheService.insert(
            "AAPL",                    // Символ Apple
            MarketType.STOCK,          // Тип рынка
            Period.M5,                 // 5-минутный период
            System.currentTimeMillis(), // Текущее время
            150.0,                     // Open
            152.0,                     // High
            149.0,                     // Low
            151.0,                     // Close
            1000000.0,                 // Volume
            1                          // Tick
        );
        log.info("Candle inserted successfully");
    } catch (IllegalStateException e) {
        log.warn("Cache is under high load, retrying later");
        // Повторить попытку позже
    }
}
```

### 3. Пакетная вставка свечей

```java
public void insertBatchCandles() {
    // Создание массива свечей
    Candle[] candles = new Candle[100];
    long baseTime = System.currentTimeMillis();
    
    for (int i = 0; i < 100; i++) {
        Candle candle = CandleUtil.newHeapCandle(
            100.0 + i * 0.1,  // Open
            101.0 + i * 0.1,  // High
            99.0 + i * 0.1,   // Low
            100.5 + i * 0.1,  // Close
            1000000.0,        // Volume
            baseTime + i * 60000, // Время (каждую минуту)
            i                 // Tick
        );
        candles[i] = candle;
    }
    
    // Пакетная вставка
    sharedTimeSeriesCacheService.insertAll(
        "AAPL",
        MarketType.STOCK,
        Period.M1,
        candles,
        0,
        candles.length
    );
    
    // Освобождение ресурсов
    CandleUtil.release(candles);
    log.info("Batch of 100 candles inserted successfully");
}
```

### 4. Чтение исторических данных

```java
public List<Candle> getHistoricalData(String symbol, long startTime, 
                                     long endTime, int maxCount) {
    Candle[] result = new Candle[maxCount];
    
    CandleTimeSeriesCache cache = sharedTimeSeriesCacheService.getCache(MarketType.STOCK, Period.M5);
    if (cache == null) {
        log.error("Cache not found for STOCK:M5");
        return new ArrayList<>();
    }
    
    int count = cache.batchRead(
        result,                    // Массив для результатов
        symbol,                    // Символ
        MarketType.STOCK,          // Тип рынка
        Period.M5.getDurationId(), // 5-минутный период
        startTime,                 // Начальное время
        0,                         // Смещение
        maxCount,                  // Максимальное количество
        Direction.FORWARD,         // Вперед по времени
        SearchMode.EXACT           // Точный поиск
    );
    
    // Преобразование в список
    List<Candle> candles = new ArrayList<>();
    for (int i = 0; i < count; i++) {
        Candle copy = CandleUtil.newHeapCandle();
        CandleUtil.copy(result[i], copy);
        candles.add(copy);
    }
    
    log.info("Retrieved {} candles for symbol {}", count, symbol);
    return candles;
}
```

## Продвинутые сценарии

### 1. Реал-тайм обработка рыночных данных

```java
@Component
public class MarketDataProcessor {
    
    @Autowired
    private SharedTimeSeriesCacheService sharedTimeSeriesCacheService;
    
    private final Map<String, Candle> currentCandles = new ConcurrentHashMap<>();
    
    public void processTick(String symbol, TickData tick) {
        // Получение или создание текущей свечи
        Candle currentCandle = currentCandles.computeIfAbsent(symbol, 
            s -> CandleUtil.newHeapCandle());
        
        long currentTime = System.currentTimeMillis();
        long periodStart = getPeriodStart(currentTime, Period.M1);
        
        // Если период изменился, сохраняем предыдущую свечу
        if (currentCandle.getTime() != 0 && 
            getPeriodStart(currentCandle.getTime(), Period.M1) != periodStart) {
            
            // Вставка завершенной свечи
            sharedTimeSeriesCacheService.insert(
                symbol,
                MarketType.STOCK,
                Period.M1,
                currentCandle.getTime(),
                currentCandle.getOpen(),
                currentCandle.getHigh(),
                currentCandle.getLow(),
                currentCandle.getClose(),
                currentCandle.getVolume(),
                currentCandle.getTick()
            );
            
            // Создание новой свечи
            CandleUtil.clear(currentCandle);
            currentCandle.setTime(periodStart);
            currentCandle.setOpen(tick.getPrice());
            currentCandle.setHigh(tick.getPrice());
            currentCandle.setLow(tick.getPrice());
            currentCandle.setClose(tick.getPrice());
            currentCandle.setVolume(tick.getVolume());
            currentCandle.setTick(1);
        } else {
            // Обновление текущей свечи
            if (currentCandle.getTime() == 0) {
                currentCandle.setTime(periodStart);
                currentCandle.setOpen(tick.getPrice());
            }
            currentCandle.setHigh(Math.max(currentCandle.getHigh(), tick.getPrice()));
            currentCandle.setLow(Math.min(currentCandle.getLow(), tick.getPrice()));
            currentCandle.setClose(tick.getPrice());
            currentCandle.setVolume(currentCandle.getVolume() + tick.getVolume());
            currentCandle.setTick(currentCandle.getTick() + 1);
        }
    }
    
    private long getPeriodStart(long timestamp, Period period) {
        return timestamp - (timestamp % period.getTimePeriod());
    }
}
```

### 2. Анализ технических индикаторов

```java
@Service
public class TechnicalAnalysisService {
    
    @Autowired
    private CandleTimeSeriesCache cache;
    
    public double calculateSMA(String symbol, int period, long endTime) {
        Candle[] candles = new Candle[period];
        
        int count = cache.batchRead(
            candles,
            symbol,
            MarketType.STOCK,
            Period.M5.getTimePeriod(),
            endTime,
            0,
            period,
            Direction.BACKWARD, // Назад по времени
            SearchMode.EXACT
        );
        
        if (count < period) {
            throw new IllegalArgumentException("Not enough data for SMA calculation");
        }
        
        double sum = 0.0;
        for (int i = 0; i < period; i++) {
            sum += candles[i].getClose();
        }
        
        return sum / period;
    }
    
    public Map<String, Double> calculateRSI(String symbol, int period, long endTime) {
        Candle[] candles = new Candle[period + 1];
        
        int count = cache.batchRead(
            candles,
            symbol,
            MarketType.STOCK,
            Period.M5.getTimePeriod(),
            endTime,
            0,
            period + 1,
            Direction.BACKWARD,
            SearchMode.EXACT
        );
        
        if (count < period + 1) {
            throw new IllegalArgumentException("Not enough data for RSI calculation");
        }
        
        double gains = 0.0;
        double losses = 0.0;
        
        for (int i = 1; i <= period; i++) {
            double change = candles[i-1].getClose() - candles[i].getClose();
            if (change > 0) {
                gains += change;
            } else {
                losses += Math.abs(change);
            }
        }
        
        double avgGain = gains / period;
        double avgLoss = losses / period;
        double rs = avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));
        
        Map<String, Double> result = new HashMap<>();
        result.put("rsi", rsi);
        result.put("avgGain", avgGain);
        result.put("avgLoss", avgLoss);
        
        return result;
    }
}
```

### 3. Мониторинг производительности

```java
@Component
public class PerformanceMonitor {
    
    private final AtomicLong writeCount = new AtomicLong();
    private final AtomicLong readCount = new AtomicLong();
    private final AtomicLong writeLatency = new AtomicLong();
    private final AtomicLong readLatency = new AtomicLong();
    
    @Autowired
    private CandleTimeSeriesCache cache;
    
    public void recordWrite(String symbol, long duration, long timestamp,
                           double open, double high, double low, double close,
                           double volume, int tick) {
        long startTime = System.nanoTime();
        
        try {
            cache.insert(symbol, duration, timestamp, open, high, low, close, volume, tick);
            writeCount.incrementAndGet();
        } finally {
            long endTime = System.nanoTime();
            writeLatency.addAndGet(endTime - startTime);
        }
    }
    
    public int recordRead(Candle[] destination, String symbol, MarketType marketType,
                         long duration, long searchTimestamp, int offset, int count,
                         Direction direction, SearchMode mode) {
        long startTime = System.nanoTime();
        
        try {
            int result = cache.batchRead(destination, symbol, marketType, duration,
                                       searchTimestamp, offset, count, direction, mode);
            readCount.incrementAndGet();
            return result;
        } finally {
            long endTime = System.nanoTime();
            readLatency.addAndGet(endTime - startTime);
        }
    }
    
    public PerformanceMetrics getMetrics() {
        long writes = writeCount.get();
        long reads = readCount.get();
        double avgWriteLatency = writes > 0 ? (double) writeLatency.get() / writes / 1_000_000 : 0;
        double avgReadLatency = reads > 0 ? (double) readLatency.get() / reads / 1_000_000 : 0;
        
        return new PerformanceMetrics(writes, reads, avgWriteLatency, avgReadLatency);
    }
    
    public static class PerformanceMetrics {
        private final long writeCount;
        private final long readCount;
        private final double avgWriteLatencyMs;
        private final double avgReadLatencyMs;
        
        // Конструктор и геттеры...
    }
}
```

### 4. Интеграция с внешними системами

```java
@RestController
@RequestMapping("/api/ohlc")
public class OhlcController {
    
    @Autowired
    private CandleTimeSeriesCache cache;
    
    @PostMapping("/insert")
    public ResponseEntity<String> insertCandle(@RequestBody CandleRequest request) {
        try {
            cache.insert(
                request.getSymbol(),
                request.getDuration(),
                request.getTimestamp(),
                request.getOpen(),
                request.getHigh(),
                request.getLow(),
                request.getClose(),
                request.getVolume(),
                request.getTick()
            );
            return ResponseEntity.ok("Candle inserted successfully");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body("Cache is under high load");
        }
    }
    
    @GetMapping("/data/{symbol}")
    public ResponseEntity<List<CandleResponse>> getData(
            @PathVariable String symbol,
            @RequestParam long startTime,
            @RequestParam long endTime,
            @RequestParam(defaultValue = "1000") int maxCount) {
        
        Candle[] result = new Candle[maxCount];
        
        int count = cache.batchRead(
            result,
            symbol,
            MarketType.STOCK,
            Period.M5.getTimePeriod(),
            startTime,
            0,
            maxCount,
            Direction.FORWARD,
            SearchMode.EXACT
        );
        
        List<CandleResponse> response = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Candle candle = result[i];
            response.add(new CandleResponse(
                candle.getTime(),
                candle.getOpen(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getVolume(),
                candle.getTick()
            ));
        }
        
        return ResponseEntity.ok(response);
    }
    
    public static class CandleRequest {
        private String symbol;
        private long duration;
        private long timestamp;
        private double open, high, low, close, volume;
        private int tick;
        
        // Геттеры и сеттеры...
    }
    
    public static class CandleResponse {
        private long time;
        private double open, high, low, close, volume;
        private int tick;
        
        // Конструктор, геттеры и сеттеры...
    }
}
```

## Обработка ошибок

### 1. Обработка высокой нагрузки

```java
public class CacheLoadHandler {
    
    private final CandleTimeSeriesCache cache;
    private final Queue<CandleData> retryQueue = new ConcurrentLinkedQueue<>();
    
    public void insertWithRetry(String symbol, long duration, long timestamp,
                               double open, double high, double low, double close,
                               double volume, int tick) {
        try {
            cache.insert(symbol, duration, timestamp, open, high, low, close, volume, tick);
        } catch (IllegalStateException e) {
            // Добавляем в очередь для повторной попытки
            retryQueue.offer(new CandleData(symbol, duration, timestamp, 
                                          open, high, low, close, volume, tick));
            log.warn("Cache under high load, queued for retry");
        }
    }
    
    @Scheduled(fixedRate = 1000) // Каждую секунду
    public void processRetryQueue() {
        CandleData data;
        while ((data = retryQueue.poll()) != null) {
            try {
                cache.insert(data.symbol, data.duration, data.timestamp,
                           data.open, data.high, data.low, data.close,
                           data.volume, data.tick);
                log.info("Retry successful for symbol: {}", data.symbol);
            } catch (IllegalStateException e) {
                // Возвращаем в очередь для следующей попытки
                retryQueue.offer(data);
                log.warn("Retry failed for symbol: {}", data.symbol);
            }
        }
    }
    
    private static class CandleData {
        final String symbol;
        final long duration, timestamp;
        final double open, high, low, close, volume;
        final int tick;
        
        // Конструктор...
    }
}
```

### 2. Валидация данных

```java
@Component
public class DataValidator {
    
    public boolean validateCandle(String symbol, long timestamp, 
                                 double open, double high, double low, 
                                 double close, double volume) {
        
        // Проверка символа
        if (symbol == null || symbol.length() > 24) {
            log.error("Invalid symbol: {}", symbol);
            return false;
        }
        
        // Проверка временной метки
        if (timestamp <= 0 || timestamp > System.currentTimeMillis() + 86400000) {
            log.error("Invalid timestamp: {}", timestamp);
            return false;
        }
        
        // Проверка цен
        if (open <= 0 || high <= 0 || low <= 0 || close <= 0) {
            log.error("Invalid prices: O={}, H={}, L={}, C={}", open, high, low, close);
            return false;
        }
        
        // Проверка логики OHLC
        if (high < Math.max(open, close) || low > Math.min(open, close)) {
            log.error("Invalid OHLC logic: O={}, H={}, L={}, C={}", open, high, low, close);
            return false;
        }
        
        // Проверка объема
        if (volume < 0) {
            log.error("Invalid volume: {}", volume);
            return false;
        }
        
        return true;
    }
}
```

## Заключение

Эти примеры демонстрируют основные возможности системы OHLC Cache и показывают, как эффективно использовать её для различных сценариев работы с финансовыми данными.

Для получения дополнительной информации обратитесь к:
- [API Reference](API_REFERENCE.md)
- [Архитектура](ARCHITECTURE.md)
- [Производительность](PERFORMANCE.md) 