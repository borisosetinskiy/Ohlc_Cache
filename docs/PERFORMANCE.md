# Руководство по производительности - OHLC Cache

## Обзор

Данное руководство описывает методы оптимизации производительности системы OHLC Cache и содержит рекомендации по достижению максимальной пропускной способности.

## Метрики производительности

### Ключевые показатели

#### 1. Пропускная способность
- **Запись:** 100,000+ свечей/сек
- **Чтение:** 1,000,000+ свечей/сек
- **Латентность:** <1 мс для одиночных операций

#### 2. Использование ресурсов
- **Память:** ~52 байта на свечу
- **CPU:** Оптимизировано для минимального использования
- **Диск:** Минимальные операции ввода-вывода

#### 3. Масштабируемость
- **Горизонтальная:** Линейное масштабирование
- **Вертикальная:** Эффективное использование ресурсов

## Оптимизации на уровне JVM

### Настройки памяти

#### Рекомендуемые параметры
```bash
# Базовые настройки
-Xms8g -Xmx32g

# Использование G1GC
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=32m
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40

# Оптимизации для off-heap
-XX:+UnlockExperimentalVMOptions
-XX:+UseZGC
-XX:+UseTransparentHugePages
```

#### Мониторинг памяти
```bash
# Анализ использования heap
jstat -gc <pid> 1000

# Анализ off-heap памяти
jmap -dump:format=b,file=heap.hprof <pid>
```

### Оптимизации компилятора

#### JIT оптимизации
```bash
# Включение агрессивных оптимизаций
-XX:+AggressiveOpts
-XX:+OptimizeStringConcat
-XX:+UseCompressedOops
-XX:+UseCompressedClassPointers

# Настройка компилятора
-XX:CompileThreshold=1000
-XX:MaxInlineSize=35
-XX:FreqInlineSize=325
```

## Оптимизации на уровне приложения

### Управление памятью

#### 1. Flyweight паттерн
```java
// Переиспользование объектов Candle
Candle reusableCandle = CandleUtil.newNativeCandle();

// Использование в циклах
for (int i = 0; i < candles.length; i++) {
    chunkValue.readCandle(i, reusableCandle);
    // Обработка данных
}
```

#### 2. Пул объектов
```java
// Создание пула для переиспользования
private final Map<String, CandleChunkValue> chunkValues = new ConcurrentHashMap<>();

CandleChunkValue getChunk() {
    var key = Thread.currentThread().threadId();
    return chunkValues.computeIfAbsent("T_" + key, s -> new CandleChunkValue());
}
```

### Оптимизации алгоритмов

#### 1. Бинарный поиск
```java
public int binarySearch(long timestamp) {
    if (page == null) return -1;
    int low = 0, high = getCount() - 1;
    while (low <= high) {
        int mid = (low + high) >>> 1;  // Оптимизированное деление на 2
        long midTs = page.readLong(HEADER_SIZE + (long) mid * CANDLE_SIZE + 40);
        if (midTs < timestamp) low = mid + 1;
        else if (midTs > timestamp) high = mid - 1;
        else return mid;
    }
    return -(low + 1);
}
```

#### 2. Пакетная обработка
```java
// Группировка свечей по чанкам для минимизации операций
Map<Long, List<Candle>> candlesByChunk = new TreeMap<>();
for (Candle candle : candles) {
    long chunkTs = getChunkStartTimestamp(candle.getTime());
    candlesByChunk.computeIfAbsent(chunkTs, ts -> new ArrayList<>()).add(candle);
}
```

### Оптимизации ввода-вывода

#### 1. Минимизация операций записи
```java
// Использование буферизации для записи
private void writeCandleToPage(long offset, Candle candle) {
    page.writeDouble(offset, candle.getHigh());
    page.writeDouble(offset + 8, candle.getLow());
    page.writeDouble(offset + 16, candle.getOpen());
    page.writeDouble(offset + 24, candle.getClose());
    page.writeDouble(offset + 32, candle.getVolume());
    page.writeLong(offset + 40, candle.getTime());
    page.writeInt(offset + 48, candle.getTick());
}
```

#### 2. Оптимизация чтения
```java
// Прямой доступ к памяти для чтения
public void readCandle(int index, Candle flyweight) {
    long offset = HEADER_SIZE + (long) index * CANDLE_SIZE;
    flyweight.bytesStore(page, offset, CANDLE_SIZE);
}
```

## Оптимизации на уровне системы

### Настройки операционной системы

#### Linux оптимизации
```bash
# Увеличение лимитов файловых дескрипторов
echo "* soft nofile 65536" >> /etc/security/limits.conf
echo "* hard nofile 65536" >> /etc/security/limits.conf

# Оптимизация сети
echo "net.core.rmem_max = 16777216" >> /etc/sysctl.conf
echo "net.core.wmem_max = 16777216" >> /etc/sysctl.conf
echo "net.core.rmem_default = 262144" >> /etc/sysctl.conf
echo "net.core.wmem_default = 262144" >> /etc/sysctl.conf

# Оптимизация памяти
echo "vm.swappiness = 1" >> /etc/sysctl.conf
echo "vm.dirty_ratio = 15" >> /etc/sysctl.conf
echo "vm.dirty_background_ratio = 5" >> /etc/sysctl.conf

# Применение изменений
sysctl -p
```

#### Настройки диска
```bash
# Использование noatime для файловых систем
mount -o remount,noatime /data

# Настройка I/O scheduler
echo "deadline" > /sys/block/sda/queue/scheduler
```

### Мониторинг производительности

#### Системные метрики
```bash
# Мониторинг CPU
top -p <pid> -H

# Мониторинг памяти
free -h
cat /proc/meminfo

# Мониторинг диска
iostat -x 1
iotop -p <pid>

# Мониторинг сети
netstat -i
ss -tulpn
```

#### JVM метрики
```bash
# GC статистика
jstat -gc <pid> 1000

# Потоки
jstack <pid> > thread_dump.txt

# Heap dump
jmap -dump:format=b,file=heap.hprof <pid>
```

## Профилирование

### Инструменты профилирования

#### 1. JProfiler
```bash
# Запуск с JProfiler
java -agentpath:/path/to/jprofiler/bin/linux-x64/libjprofilerti.so=port=8849 \
     -jar Ohlc_Cache-0.0.1.jar
```

#### 2. VisualVM
```bash
# Подключение к процессу
jvisualvm --openpid <pid>
```

#### 3. Async-profiler
```bash
# Профилирование CPU
./profiler.sh -d 30 -f profile.html <pid>

# Профилирование аллокаций
./profiler.sh -d 30 -e alloc -f alloc.html <pid>
```

### Анализ производительности

#### 1. Анализ узких мест
```java
// Измерение времени выполнения
long startTime = System.nanoTime();
// Операция
long endTime = System.nanoTime();
long duration = (endTime - startTime) / 1_000_000; // в миллисекундах
```

#### 2. Профилирование памяти
```java
// Мониторинг аллокаций
Runtime runtime = Runtime.getRuntime();
long usedMemory = runtime.totalMemory() - runtime.freeMemory();
```

## Бенчмарки

### Тесты производительности

#### 1. Тест записи
```java
@Test
public void testWritePerformance() {
    long startTime = System.currentTimeMillis();
    int count = 1000000;
    
    for (int i = 0; i < count; i++) {
        candleTimeSeriesCache.insert(
            "TEST", 
            Period.M1.getTimePeriod(),
            System.currentTimeMillis() + i * 60000,
            100.0, 101.0, 99.0, 100.5, 1000.0, i
        );
    }
    
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    double rate = (double) count / duration * 1000; // свечей в секунду
    
    System.out.println("Write rate: " + rate + " candles/sec");
}
```

#### 2. Тест чтения
```java
@Test
public void testReadPerformance() {
    Candle[] result = new Candle[10000];
    long startTime = System.currentTimeMillis();
    
    int count = candleTimeSeriesCache.batchRead(
        result, "TEST", MarketType.STOCK,
        Period.M1.getTimePeriod(), System.currentTimeMillis(),
        0, 10000, Direction.FORWARD, SearchMode.EXACT
    );
    
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    double rate = (double) count / duration * 1000; // свечей в секунду
    
    System.out.println("Read rate: " + rate + " candles/sec");
}
```

### Метрики бенчмарков

#### Ожидаемые результаты
- **Запись одиночных свечей:** 50,000-100,000 свечей/сек
- **Пакетная запись:** 200,000-500,000 свечей/сек
- **Чтение последовательное:** 1,000,000-5,000,000 свечей/сек
- **Чтение случайное:** 100,000-500,000 свечей/сек
- **Латентность 95-го процентиля:** <5 мс
- **Латентность 99-го процентиля:** <10 мс

## Оптимизации для конкретных сценариев

### Высокочастотная торговля

#### Настройки для низкой латентности
```bash
# JVM настройки для HFT
-XX:+UseG1GC
-XX:MaxGCPauseMillis=10
-XX:+UnlockExperimentalVMOptions
-XX:+UseZGC
-XX:+UseTransparentHugePages
-XX:+AlwaysPreTouch
```

#### Оптимизации приложения
```java
// Предварительное выделение памяти
public void preallocateMemory() {
    for (int i = 0; i < 1000; i++) {
        CandleChunkValue chunk = new CandleChunkValue();
        chunk.init(period.getPerChunk());
        chunkPool.offer(chunk);
    }
}
```

### Долгосрочное хранение

#### Настройки для больших объемов
```bash
# JVM настройки для больших данных
-Xms16g -Xmx64g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=500
-XX:G1HeapRegionSize=64m
```

#### Оптимизации хранения
```java
// Сжатие старых данных
public void compressOldData() {
    // Реализация сжатия для старых чанков
}
```

## Мониторинг в реальном времени

### Метрики приложения

#### 1. Кастомные метрики
```java
@Component
public class PerformanceMetrics {
    private final AtomicLong writeCount = new AtomicLong();
    private final AtomicLong readCount = new AtomicLong();
    private final AtomicLong writeLatency = new AtomicLong();
    private final AtomicLong readLatency = new AtomicLong();
    
    public void recordWrite(long latency) {
        writeCount.incrementAndGet();
        writeLatency.addAndGet(latency);
    }
    
    public void recordRead(long latency) {
        readCount.incrementAndGet();
        readLatency.addAndGet(latency);
    }
    
    public double getAverageWriteLatency() {
        long count = writeCount.get();
        return count > 0 ? (double) writeLatency.get() / count : 0;
    }
}
```

#### 2. Интеграция с Micrometer
```java
@Component
public class CacheMetrics {
    private final MeterRegistry meterRegistry;
    private final Timer writeTimer;
    private final Timer readTimer;
    private final Counter writeCounter;
    private final Counter readCounter;
    
    public CacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.writeTimer = Timer.builder("cache.write.latency").register(meterRegistry);
        this.readTimer = Timer.builder("cache.read.latency").register(meterRegistry);
        this.writeCounter = Counter.builder("cache.write.count").register(meterRegistry);
        this.readCounter = Counter.builder("cache.read.count").register(meterRegistry);
    }
    
    public void recordWrite(Runnable operation) {
        writeTimer.record(operation);
        writeCounter.increment();
    }
}
```

### Алерты и уведомления

#### Настройка алертов
```yaml
# Prometheus алерты
groups:
  - name: ohlc-cache
    rules:
      - alert: HighWriteLatency
        expr: histogram_quantile(0.95, cache_write_latency_seconds) > 0.01
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High write latency detected"
          
      - alert: HighReadLatency
        expr: histogram_quantile(0.95, cache_read_latency_seconds) > 0.005
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High read latency detected"
```

## Рекомендации по оптимизации

### Общие рекомендации

1. **Мониторинг:** Постоянно отслеживайте метрики производительности
2. **Профилирование:** Регулярно проводите профилирование для выявления узких мест
3. **Тестирование:** Выполняйте нагрузочное тестирование перед развертыванием
4. **Оптимизация:** Применяйте оптимизации поэтапно, измеряя эффект каждого изменения

### Специфические рекомендации

1. **Для высокочастотной торговли:** Фокусируйтесь на снижении латентности
2. **Для аналитики:** Оптимизируйте пропускную способность чтения
3. **Для долгосрочного хранения:** Балансируйте между производительностью и эффективностью использования памяти

### Избегание типичных ошибок

1. **Не используйте синхронизацию без необходимости**
2. **Избегайте создания объектов в горячих циклах**
3. **Не игнорируйте мониторинг GC**
4. **Не используйте неоптимизированные алгоритмы поиска** 