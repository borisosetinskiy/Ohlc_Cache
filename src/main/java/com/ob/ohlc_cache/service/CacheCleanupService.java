package com.ob.ohlc_cache.service;

import com.ob.ohlc_cache.model.type.MarketType;
import com.ob.ohlc_cache.model.type.Period;
import com.ob.ohlc_cache.service.chronicle.CandleTimeSeriesCache;
import com.ob.ohlc_cache.service.chronicle.SharedTimeSeriesCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Сервис для автоматической очистки устаревших чанков в кэше.
 */
@Slf4j
@Service
public class CacheCleanupService {

    @Autowired
    private SharedTimeSeriesCacheService sharedTimeSeriesCacheService;

    @Value("${cache.cleanup.retention-period-ms:604800000}") // 7 дней по умолчанию
    private long retentionPeriodMs;

    @Value("${cache.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    /**
     * Автоматическая очистка устаревших чанков.
     * Запускается каждый час по умолчанию.
     */
    @Scheduled(fixedRate = 3600000) // 1 час
    public void scheduledCleanup() {
        if (!cleanupEnabled) {
            log.debug("Cache cleanup is disabled");
            return;
        }

        try {
            log.info("Starting scheduled cache cleanup with retention period: {} ms", retentionPeriodMs);
            
            long startTime = System.currentTimeMillis();
            int totalDeletedCount = 0;
            
            // Очищаем все кэши для всех MarketType и Period
            for (MarketType marketType : MarketType.values()) {
                for (Period period : Period.values()) {
                    CandleTimeSeriesCache cache = sharedTimeSeriesCacheService.getCache(marketType, period);
                    if (cache != null) {
                        int deletedCount = cache.cleanupExpiredChunks(retentionPeriodMs);
                        totalDeletedCount += deletedCount;
                        log.debug("Cleaned {} chunks for {}:{}", deletedCount, marketType, period);
                    }
                }
            }
            
            long endTime = System.currentTimeMillis();
            
            log.info("Scheduled cleanup completed: deleted {} chunks in {} ms", 
                    totalDeletedCount, endTime - startTime);
            
        } catch (Exception e) {
            log.error("Error during scheduled cache cleanup", e);
        }
    }

    /**
     * Ручной запуск очистки.
     * @param customRetentionPeriodMs кастомный период хранения в миллисекундах
     * @return количество удаленных чанков
     */
    public int manualCleanup(long customRetentionPeriodMs) {
        if (customRetentionPeriodMs <= 0) {
            customRetentionPeriodMs = retentionPeriodMs;
        }
        
        log.info("Starting manual cache cleanup with retention period: {} ms", customRetentionPeriodMs);
        
        long startTime = System.currentTimeMillis();
        int totalDeletedCount = 0;
        
        // Очищаем все кэши для всех MarketType и Period
        for (MarketType marketType : MarketType.values()) {
            for (Period period : Period.values()) {
                CandleTimeSeriesCache cache = sharedTimeSeriesCacheService.getCache(marketType, period);
                if (cache != null) {
                    int deletedCount = cache.cleanupExpiredChunks(customRetentionPeriodMs);
                    totalDeletedCount += deletedCount;
                    log.debug("Cleaned {} chunks for {}:{}", deletedCount, marketType, period);
                }
            }
        }
        
        long endTime = System.currentTimeMillis();
        
        log.info("Manual cleanup completed: deleted {} chunks in {} ms", 
                totalDeletedCount, endTime - startTime);
        
        return totalDeletedCount;
    }

    /**
     * Ручной запуск очистки с дефолтным периодом хранения.
     * @return количество удаленных чанков
     */
    public int manualCleanup() {
        return manualCleanup(retentionPeriodMs);
    }

    /**
     * Удаление конкретного чанка.
     * @param symbol символ финансового инструмента
     * @param marketType тип рынка
     * @param period период
     * @param chunkStartTimestamp временная метка начала чанка
     * @return true если чанк был найден и удален
     */
    public boolean deleteSpecificChunk(String symbol, MarketType marketType, Period period, long chunkStartTimestamp) {
        log.info("Deleting specific chunk: symbol={}, marketType={}, period={}, chunkStartTimestamp={}", 
                symbol, marketType, period, chunkStartTimestamp);
        
        CandleTimeSeriesCache cache = sharedTimeSeriesCacheService.getCache(marketType, period);
        if (cache == null) {
            log.warn("Cache not found for {}:{}", marketType, period);
            return false;
        }
        
        boolean deleted = cache.deleteChunk(symbol, period.getDurationId(), chunkStartTimestamp);
        
        if (deleted) {
            log.info("Successfully deleted specific chunk");
        } else {
            log.warn("Failed to delete specific chunk - not found");
        }
        
        return deleted;
    }

    /**
     * Получение статистики кэша.
     * @return статистика кэша
     */
    public CandleTimeSeriesCache.CacheStatistics getCacheStatistics() {
        int totalChunks = 0;
        int expiredChunks = 0;
        long totalCandles = 0;
        long currentTime = System.currentTimeMillis();
        
        // Собираем статистику по всем кэшам
        for (MarketType marketType : MarketType.values()) {
            for (Period period : Period.values()) {
                CandleTimeSeriesCache cache = sharedTimeSeriesCacheService.getCache(marketType, period);
                if (cache != null) {
                    CandleTimeSeriesCache.CacheStatistics stats = cache.getStatistics();
                    totalChunks += stats.getTotalChunks();
                    expiredChunks += stats.getExpiredChunks();
                    totalCandles += stats.getTotalCandles();
                }
            }
        }
        
        return new CandleTimeSeriesCache.CacheStatistics(totalChunks, expiredChunks, totalCandles, currentTime);
    }

    /**
     * Получение текущего периода хранения.
     * @return период хранения в миллисекундах
     */
    public long getRetentionPeriodMs() {
        return retentionPeriodMs;
    }

    /**
     * Установка периода хранения.
     * @param retentionPeriodMs период хранения в миллисекундах
     */
    public void setRetentionPeriodMs(long retentionPeriodMs) {
        this.retentionPeriodMs = retentionPeriodMs;
        log.info("Retention period updated to: {} ms", retentionPeriodMs);
    }

    /**
     * Проверка, включена ли автоматическая очистка.
     * @return true если очистка включена
     */
    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    /**
     * Включение/выключение автоматической очистки.
     * @param cleanupEnabled true для включения очистки
     */
    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
        log.info("Cache cleanup {}abled", cleanupEnabled ? "en" : "dis");
    }

    /**
     * Получение информации о настройках очистки.
     * @return строка с информацией о настройках
     */
    public String getCleanupInfo() {
        return String.format("Cleanup enabled: %s, Retention period: %d ms (%d days)", 
                cleanupEnabled, 
                retentionPeriodMs, 
                TimeUnit.MILLISECONDS.toDays(retentionPeriodMs));
    }
} 