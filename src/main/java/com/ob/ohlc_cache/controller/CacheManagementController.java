package com.ob.ohlc_cache.controller;

import com.ob.ohlc_cache.model.type.MarketType;
import com.ob.ohlc_cache.model.type.Period;
import com.ob.ohlc_cache.service.CacheCleanupService;
import com.ob.ohlc_cache.service.chronicle.CandleTimeSeriesCache;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST контроллер для управления кэшем и очисткой.
 */
@Slf4j
@RestController
@RequestMapping("/api/cache")
@Data
public class CacheManagementController {

    private final CacheCleanupService cacheCleanupService;


    @GetMapping("/statistics")
    public ResponseEntity<CandleTimeSeriesCache.CacheStatistics> getStatistics() {
        log.info("Getting cache statistics");
        CandleTimeSeriesCache.CacheStatistics stats = cacheCleanupService.getCacheStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Получение информации о настройках очистки.
     */
    @GetMapping("/cleanup/info")
    public ResponseEntity<Map<String, Object>> getCleanupInfo() {
        log.info("Getting cleanup configuration info");
        
        Map<String, Object> info = new HashMap<>();
        info.put("cleanupInfo", cacheCleanupService.getCleanupInfo());
        info.put("enabled", cacheCleanupService.isCleanupEnabled());
        info.put("retentionPeriodMs", cacheCleanupService.getRetentionPeriodMs());
        info.put("retentionPeriodDays", cacheCleanupService.getRetentionPeriodMs() / (1000 * 60 * 60 * 24));
        
        return ResponseEntity.ok(info);
    }

    /**
     * Ручной запуск очистки.
     */
    @PostMapping("/cleanup/manual")
    public ResponseEntity<Map<String, Object>> manualCleanup(
            @RequestParam(defaultValue = "0") long retentionPeriodMs) {
        
        log.info("Starting manual cleanup with retention period: {} ms", retentionPeriodMs);
        
        long startTime = System.currentTimeMillis();
        int deletedCount = cacheCleanupService.manualCleanup(retentionPeriodMs);
        long endTime = System.currentTimeMillis();
        
        Map<String, Object> result = new HashMap<>();
        result.put("deletedChunks", deletedCount);
        result.put("executionTimeMs", endTime - startTime);
        result.put("retentionPeriodMs", retentionPeriodMs);
        result.put("success", true);
        
        log.info("Manual cleanup completed: deleted {} chunks in {} ms", deletedCount, endTime - startTime);
        
        return ResponseEntity.ok(result);
    }

    /**
     * Удаление конкретного чанка.
     */
    @DeleteMapping("/chunks/{symbol}")
    public ResponseEntity<Map<String, Object>> deleteChunk(
            @PathVariable String symbol,
            @RequestParam MarketType marketType,
            @RequestParam Period period,
            @RequestParam long chunkStartTimestamp) {
        
        log.info("Deleting chunk: symbol={}, marketType={}, period={}, chunkStartTimestamp={}", 
                symbol, marketType, period, chunkStartTimestamp);
        
        boolean deleted = cacheCleanupService.deleteSpecificChunk(symbol, marketType, period, chunkStartTimestamp);
        
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("symbol", symbol);
        result.put("marketType", marketType);
        result.put("period", period);
        result.put("chunkStartTimestamp", chunkStartTimestamp);
        
        if (deleted) {
            log.info("Successfully deleted chunk");
            return ResponseEntity.ok(result);
        } else {
            log.warn("Failed to delete chunk - not found");
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Обновление периода хранения.
     */
    @PutMapping("/cleanup/retention")
    public ResponseEntity<Map<String, Object>> updateRetentionPeriod(
            @RequestParam long retentionPeriodMs) {
        
        if (retentionPeriodMs <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Retention period must be positive");
            error.put("success", false);
            return ResponseEntity.badRequest().body(error);
        }
        
        log.info("Updating retention period to: {} ms", retentionPeriodMs);
        
        cacheCleanupService.setRetentionPeriodMs(retentionPeriodMs);
        
        Map<String, Object> result = new HashMap<>();
        result.put("retentionPeriodMs", retentionPeriodMs);
        result.put("retentionPeriodDays", retentionPeriodMs / (1000 * 60 * 60 * 24));
        result.put("success", true);
        
        return ResponseEntity.ok(result);
    }

    /**
     * Включение/выключение автоматической очистки.
     */
    @PutMapping("/cleanup/enabled")
    public ResponseEntity<Map<String, Object>> setCleanupEnabled(
            @RequestParam boolean enabled) {
        
        log.info("Setting cleanup enabled to: {}", enabled);
        
        cacheCleanupService.setCleanupEnabled(enabled);
        
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", enabled);
        result.put("success", true);
        
        return ResponseEntity.ok(result);
    }

} 