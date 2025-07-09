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
}
