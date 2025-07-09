# Управление жизненным циклом кэша

## Обзор

OHLC Cache поддерживает автоматическое управление жизненным циклом данных через механизмы TTL (Time To Live) и ручного удаления. Это позволяет:

- Автоматически освобождать память от устаревших данных
- Контролировать размер кэша
- Удалять конкретные чанки по требованию
- Мониторить состояние кэша

## Технические метаданные

### Структура метаданных

Каждый чанк в кэше содержит технические метаданные:

```java
public class CandleChunkValue {
    // Бизнес-метаданные
    private int count;                    // Количество свечей
    private int capacityInCandles;        // Емкость чанка
    private long firstCandleTimestamp;    // Время первой свечи
    private long lastCandleTimestamp;     // Время последней свечи
    
    // Технические метаданные
    private long createdTimestamp;        // Время создания чанка
    private long lastAccessTimestamp;     // Время последнего доступа
}
```

### Автоматическое обновление

Технические метаданные обновляются автоматически:

- `createdTimestamp` - устанавливается при создании чанка
- `lastAccessTimestamp` - обновляется при каждой операции чтения/записи

## Автоматическая очистка (TTL)

### Принцип работы

Система автоматически удаляет чанки, которые превысили период хранения:

1. **Планировщик** запускается по расписанию (по умолчанию каждый час)
2. **Проверка TTL** - для каждого чанка вычисляется возраст
3. **Удаление** - чанки старше retention period удаляются
4. **Логирование** - результаты операции записываются в лог

### Настройки

```yaml
cache:
  cleanup:
    enabled: true                    # Включение автоматической очистки
    retention-period-ms: 604800000   # Период хранения (7 дней)
    schedule-interval-ms: 3600000    # Интервал запуска (1 час)
```

### Алгоритм очистки

```java
public int cleanupExpiredChunks(long retentionPeriodMs) {
    int deletedCount = 0;
    
    for (Map.Entry<ChunkKey, CandleChunkValue> entry : chunkCache.entrySet()) {
        ChunkKey key = entry.getKey();
        CandleChunkValue chunkValue = entry.getValue();
        
        // Проверяем, истек ли TTL
        if (chunkValue.isExpired(retentionPeriodMs)) {
            // Удаляем чанк и его индекс
            if (deleteChunk(key.getSymbol(), key.getDuration(), key.getChunkStartTimestamp())) {
                deletedCount++;
            }
        }
    }
    
    return deletedCount;
}
```

## Ручное управление

### Удаление конкретного чанка

```java
// Удаление чанка по символу, типу рынка, периоду и временной метке
boolean deleted = cacheCleanupService.deleteSpecificChunk(
    "AAPL",                    // Символ
    MarketType.STOCK,          // Тип рынка
    Period.M5,                 // Период (5 минут)
    1640995200000L             // Временная метка начала чанка
);
```

### Ручная очистка

```java
// Очистка всех чанков старше 7 дней
int deletedCount = cacheCleanupService.manualCleanup(
    7 * 24 * 60 * 60 * 1000L  // 7 дней в миллисекундах
);
```

### Получение статистики

```java
// Получение статистики кэша
CandleTimeSeriesCache.CacheStatistics stats = cacheCleanupService.getCacheStatistics();
System.out.println("Total chunks: " + stats.getTotalChunks());
System.out.println("Expired chunks: " + stats.getExpiredChunks());
System.out.println("Total candles: " + stats.getTotalCandles());
```

## REST API

### Получение статистики

```bash
GET /api/cache/statistics
```

**Ответ:**
```json
{
  "totalChunks": 1500,
  "expiredChunks": 45,
  "totalCandles": 150000,
  "timestamp": 1640995200000
}
```

### Информация о настройках очистки

```bash
GET /api/cache/cleanup/info
```

**Ответ:**
```json
{
  "cleanupInfo": "Cleanup enabled: true, Retention period: 604800000 ms (7 days)",
  "enabled": true,
  "retentionPeriodMs": 604800000,
  "retentionPeriodDays": 7
}
```

### Ручной запуск очистки

```bash
POST /api/cache/cleanup/manual?retentionPeriodMs=604800000
```

**Ответ:**
```json
{
  "deletedChunks": 23,
  "executionTimeMs": 150,
  "retentionPeriodMs": 604800000,
  "success": true
}
```

### Удаление конкретного чанка

```bash
DELETE /api/cache/chunks/AAPL?marketType=STOCK&period=M5&chunkStartTimestamp=1640995200000
```

**Ответ:**
```json
{
  "deleted": true,
  "symbol": "AAPL",
  "marketType": "STOCK",
  "period": "M5",
  "chunkStartTimestamp": 1640995200000
}
```

### Обновление периода хранения

```bash
PUT /api/cache/cleanup/retention?retentionPeriodMs=86400000
```

**Ответ:**
```json
{
  "retentionPeriodMs": 86400000,
  "retentionPeriodDays": 1,
  "success": true
}
```

### Включение/выключение автоматической очистки

```bash
PUT /api/cache/cleanup/enabled?enabled=false
```

**Ответ:**
```json
{
  "enabled": false,
  "success": true
}
```

## Мониторинг и логирование

### Логи очистки

Система логирует все операции очистки:

```
INFO  - Starting scheduled cache cleanup with retention period: 604800000 ms
INFO  - Successfully deleted chunk for symbol: AAPL, duration: 300000, chunkStartTimestamp: 1640995200000
INFO  - Cleanup completed: deleted 23 expired chunks with retention period 604800000 ms
INFO  - Cache statistics after cleanup: CacheStatistics{totalChunks=1477, expiredChunks=0, totalCandles=147700, timestamp=1640995200000}
```

### Метрики производительности

- **Время выполнения очистки** - измеряется для каждой операции
- **Количество удаленных чанков** - статистика по операциям
- **Размер кэша** - мониторинг использования памяти

## Рекомендации по настройке

### Период хранения

- **Краткосрочные данные (M1, M5)**: 1-3 дня
- **Среднесрочные данные (M15, H1)**: 7-30 дней  
- **Долгосрочные данные (H4, D1)**: 30-90 дней

### Частота очистки

- **Высокая нагрузка**: каждый час
- **Средняя нагрузка**: каждые 6 часов
- **Низкая нагрузка**: раз в день

### Мониторинг

- Отслеживайте количество удаленных чанков
- Контролируйте время выполнения очистки
- Мониторьте размер кэша после очистки

## Обработка ошибок

### Типичные ошибки

1. **Чанк не найден** - при попытке удаления несуществующего чанка
2. **Ошибка блокировки** - при высокой конкурентности
3. **Ошибка индекса** - при проблемах с индексом

### Стратегии восстановления

- **Повторные попытки** для временных ошибок
- **Логирование** всех ошибок для анализа
- **Graceful degradation** - продолжение работы при частичных сбоях 