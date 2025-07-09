# API Reference - OHLC Cache

## Обзор API

OHLC Cache предоставляет набор API для работы с временными рядами финансовых данных. Все API оптимизированы для высокой производительности и работы с большими объемами данных.

## Основные интерфейсы

### CandleTimeSeriesCache

Основной интерфейс для работы с временными рядами свечей.

#### Методы вставки данных

##### insert()
```java
void insert(String symbol, long duration, long timestamp, 
           double open, double high, double low, double close, 
           double volume, int tick)
```

**Описание:** Вставляет одиночную свечу в кэш.

**Параметры:**
- `symbol` (String) - Символ финансового инструмента (макс. 24 символа)
- `duration` (long) - Длительность периода в миллисекундах
- `timestamp` (long) - Временная метка свечи в миллисекундах
- `open` (double) - Цена открытия
- `high` (double) - Максимальная цена
- `low` (double) - Минимальная цена
- `close` (double) - Цена закрытия
- `volume` (double) - Объем торгов
- `tick` (int) - Номер тика

**Пример использования:**
```java
candleTimeSeriesCache.insert(
    "AAPL",           // Символ Apple
    300000L,          // 5-минутный период
    1640995200000L,   // Временная метка
    150.0,            // Open
    152.0,            // High
    149.0,            // Low
    151.0,            // Close
    1000000.0,        // Volume
    1                 // Tick
);
```

##### batchInsert()
```java
void batchInsert(String symbol, long duration, Candle[] candles, 
                int offset, int count)
```

**Описание:** Вставляет массив свечей в кэш пакетно.

**Параметры:**
- `symbol` (String) - Символ финансового инструмента
- `duration` (long) - Длительность периода в миллисекундах
- `candles` (Candle[]) - Массив свечей для вставки
- `offset` (int) - Начальная позиция в массиве
- `count` (int) - Количество свечей для вставки

**Пример использования:**
```java
Candle[] candles = new Candle[100];
// Заполнение массива свечей...
candleTimeSeriesCache.batchInsert("AAPL", 300000L, candles, 0, 100);
```

#### Методы чтения данных

##### batchRead()
```java
int batchRead(Candle[] destination, String symbol, MarketType marketType,
              long duration, long searchTimestamp, int offset, int count,
              Direction direction, SearchMode mode)
```

**Описание:** Читает свечи из кэша по заданным критериям.

**Параметры:**
- `destination` (Candle[]) - Массив для результатов
- `symbol` (String) - Символ финансового инструмента
- `marketType` (MarketType) - Тип рынка
- `duration` (long) - Длительность периода в миллисекундах
- `searchTimestamp` (long) - Начальная временная метка для поиска
- `offset` (int) - Смещение в массиве результатов
- `count` (int) - Количество свечей для чтения
- `direction` (Direction) - Направление поиска
- `mode` (SearchMode) - Режим поиска

**Возвращает:** Количество прочитанных свечей

**Пример использования:**
```java
Candle[] result = new Candle[1000];
int count = candleTimeSeriesCache.batchRead(
    result,                    // Массив результатов
    "AAPL",                    // Символ
    MarketType.STOCK,          // Тип рынка
    300000L,                   // 5-минутный период
    1640995200000L,            // Начальная временная метка
    0,                         // Смещение
    1000,                      // Количество свечей
    Direction.FORWARD,         // Вперед по времени
    SearchMode.EXACT           // Точный поиск
);
```

### ChronicleIndexApi

API для работы с индексами временных рядов.

#### add()
```java
void add(String seriesId, Long timestamp, long durationTimestamp)
```

**Описание:** Добавляет временную метку в индекс для асинхронной обработки.

**Параметры:**
- `seriesId` (String) - Идентификатор серии
- `timestamp` (Long) - Временная метка чанка
- `durationTimestamp` (long) - Длительность группировки индекса

#### fetchChunkTimestamps()
```java
List<Long> fetchChunkTimestamps(String seriesId, long startTimestamp,
                               int count, Direction direction, 
                               long chunkDurationSec)
```

**Описание:** Получает список временных меток чанков для заданного диапазона.

**Параметры:**
- `seriesId` (String) - Идентификатор серии
- `startTimestamp` (long) - Начальная временная метка
- `count` (int) - Количество чанков
- `direction` (Direction) - Направление поиска
- `chunkDurationSec` (long) - Длительность чанка в секундах

**Возвращает:** Список временных меток чанков

## Модели данных

### Candle Interface

```java
public interface Candle extends Byteable, BytesMarshallable {
    int CANDLE_STRUCT_SIZE = 52;
    
    @Group(1) double getHigh();
    void setHigh(double high);
    
    @Group(2) double getLow();
    void setLow(double low);
    
    @Group(3) double getOpen();
    void setOpen(double open);
    
    @Group(4) double getClose();
    void setClose(double close);
    
    @Group(5) double getVolume();
    void setVolume(double volume);
    
    @Group(6) long getTime();
    void setTime(long time);
    
    @Group(7) int getTick();
    void setTick(int tick);
    
    // Утилитные методы
    default void copyReusable(Candle to);
    default Candle copy();
}
```

### Chart Model

```java
@Slf4j
@Data
public class Chart {
    private String requestId;
    private Candle lastCandle;
    private Candle[] candles;
    private Long instrumentId;
    private String symbol;
    private Period period;
    private Long currentCandleTime;
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
    
    // Методы
    public Chart(int size);
    public void clear();
    public void setEndTime(long endTime);
    public void setSearchEndTime(long searchEndTime);
    public void setCurrentCandleTime(long newValue);
}
```

## Типы данных

### Period Enum

```java
public enum Period {
    M1("1 minute", 60_000L, 1, 1, TimeUnit.DAYS, 3, 10000, ...),
    M5("5 minutes", 60_000L * 5, 5, 2, TimeUnit.DAYS, 15, 10000, ...),
    M10("10 minutes", 60_000L * 10, 10, 12, TimeUnit.DAYS, 30, 10000, ...),
    M15("15 minutes", 60_000L * 15, 15, 3, TimeUnit.DAYS, 45, 10000, ...),
    M30("30 minutes", 60_000L * 30, 30, 6, TimeUnit.DAYS, 90, 10000, ...),
    H1("1 hour", 3_600_000L, 60, 4, TimeUnit.DAYS, 180, 10000, ...),
    H2("2 hours", 3_600_000L * 2, 120, 7, TimeUnit.DAYS, 360, 10000, ...),
    H3("3 hours", 3_600_000L * 3, 180, 13, TimeUnit.DAYS, 540, 10000, ...),
    H4("4 hours", 3_600_000L * 4, 240, 8, TimeUnit.DAYS, 1644, 10000, ...),
    H8("8 hours", 3_600_000L * 8, 480, 11, TimeUnit.DAYS, 3285, 20000, ...),
    D1("1 day", 86_400_000L, 1440, 5, TimeUnit.DAYS, 10950, 20000, ...),
    D7("7 days", 86_400_000L * 7, 10080, 9, TimeUnit.DAYS, 10950, 20000, ...),
    Mo1("1 month", 86_400_000L * 31, 43830, 10, TimeUnit.DAYS, 14640, 5000, ...),
    y1("1 year", 86_400_000L * 366, 525960, 14, TimeUnit.DAYS, 14640, 2000, ...);
    
    // Методы
    public static Period getByDurationId(int id);
    public static boolean in(long t1, long t2, Period period);
}
```

### MarketType Enum

```java
public enum MarketType {
    STOCK,      // Акции
    FOREX,      // Валютные пары
    CRYPTO,     // Криптовалюты
    COMMODITY,  // Товары
    INDEX,      // Индексы
    FUTURE,     // Фьючерсы
    OPTION      // Опционы
}
```

### Direction Enum

```java
public enum Direction {
    FORWARD,    // Вперед по времени
    BACKWARD    // Назад по времени
}
```

### SearchMode Enum

```java
public enum SearchMode {
    EXACT,      // Точный поиск
    NEAREST     // Поиск ближайшего значения
}
```

## Утилиты

### CandleUtil

```java
public class CandleUtil {
    // Создание свечей
    public static Candle newNativeCandle();
    public static Candle newHeapCandle();
    public static Candle newHeapCandle(double open, double close, double high, 
                                      double low, double volume, long time, int tick);
    public static Candle[] newNativeCandleArray(int size);
    public static Candle[] newHeapCandleArray(int size);
    
    // Валидация
    public static boolean validate(Candle candle);
    
    // Операции с массивами
    public static void shiftRight(Candle[] candles, int k, int n);
    public static void shiftLeft(Candle[] candles, int k, int n);
    public static void copy(Candle from, Candle to);
    public static boolean prependIfNewer(Candle[] candles, Candle newCandle, int filledSize);
    
    // Очистка и освобождение ресурсов
    public static void clear(Candle candle);
    public static void clear(Candle[] candles);
    public static void release(Candle candle);
    public static void release(Candle[] candles);
    
    // Утилиты
    public static boolean isEmpty(Candle candle);
    public static boolean equals(Candle first, Candle second);
    public static String toString(Candle candle);
}
```

### TimeUtil

```java
public class TimeUtil {
    // Конвертация времени
    public static long toMillis(long timestamp, TimeUnit unit);
    public static long fromMillis(long millis, TimeUnit unit);
    
    // Работа с периодами
    public static long alignToPeriod(long timestamp, Period period);
    public static long getPeriodStart(long timestamp, Period period);
    public static long getPeriodEnd(long timestamp, Period period);
    
    // Валидация времени
    public static boolean isValidTimestamp(long timestamp);
    public static boolean isInRange(long timestamp, long start, long end);
}
```

## Обработка ошибок

### Исключения

#### IllegalStateException
Выбрасывается при:
- Высокой нагрузке на кэш (не удается получить блокировку)
- Попытке использования закрытого кэша

#### IndexOutOfBoundsException
Выбрасывается при:
- Попытке доступа к несуществующему индексу в чанке

#### RuntimeException
Выбрасывается при:
- Прерывании потока во время ожидания блокировки
- Ошибках создания объектов

### Рекомендации по обработке ошибок

```java
try {
    candleTimeSeriesCache.insert(symbol, duration, timestamp, 
                                open, high, low, close, volume, tick);
} catch (IllegalStateException e) {
    // Обработка высокой нагрузки
    log.warn("Cache is under high load, retrying later");
    // Повторить попытку позже
} catch (RuntimeException e) {
    // Обработка других ошибок
    log.error("Error inserting candle", e);
    // Уведомить пользователя или систему мониторинга
}
```

## Примеры использования

### Полный пример работы с кэшем

```java
@Service
public class OhlcDataService {
    
    @Autowired
    private CandleTimeSeriesCache candleTimeSeriesCache;
    
    public void processMarketData(String symbol, MarketData data) {
        try {
            // Вставка новой свечи
            candleTimeSeriesCache.insert(
                symbol,
                Period.M5.getTimePeriod(),
                data.getTimestamp(),
                data.getOpen(),
                data.getHigh(),
                data.getLow(),
                data.getClose(),
                data.getVolume(),
                data.getTick()
            );
        } catch (IllegalStateException e) {
            log.warn("Cache is under high load for symbol: {}", symbol);
            // Обработка высокой нагрузки
        }
    }
    
    public List<Candle> getHistoricalData(String symbol, long startTime, 
                                        long endTime, int maxCount) {
        Candle[] result = new Candle[maxCount];
        int count = candleTimeSeriesCache.batchRead(
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
        
        return Arrays.asList(result).subList(0, count);
    }
}
```

### Пакетная обработка данных

```java
public void batchProcessData(String symbol, List<MarketData> dataList) {
    Candle[] candles = new Candle[dataList.size()];
    
    // Преобразование данных в свечи
    for (int i = 0; i < dataList.size(); i++) {
        MarketData data = dataList.get(i);
        Candle candle = CandleUtil.newHeapCandle(
            data.getOpen(), data.getClose(), data.getHigh(),
            data.getLow(), data.getVolume(), data.getTimestamp(), data.getTick()
        );
        candles[i] = candle;
    }
    
    // Пакетная вставка
    candleTimeSeriesCache.batchInsert(
        symbol,
        Period.M5.getTimePeriod(),
        candles,
        0,
        candles.length
    );
    
    // Освобождение ресурсов
    CandleUtil.release(candles);
}
``` 