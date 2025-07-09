package com.ob.ohlc_cache.service.chronicle;

import com.ob.ohlc_cache.model.type.Direction;

import java.util.List;

public interface IndexApi extends AutoCloseable{
    void add(String seriesId, Long timestamp, long indexTimestampSec);
    List<Long> fetchChunkTimestamps(String seriesId
            , long timestamp
            , int count
            , Direction direction
            , long chunkDurationSec);
    
    /**
     * Удаляет временную метку чанка из индекса.
     * @param seriesId идентификатор серии
     * @param chunkTimestamp временная метка чанка для удаления
     * @param indexTimestampSec интервал группировки индекса
     */
    void remove(String seriesId, Long chunkTimestamp, long indexTimestampSec);
}
