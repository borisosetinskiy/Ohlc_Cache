package com.ob.ohlc_cache.model.cache;

public record IndexTask(String seriesId, long chunkTimestamp, long indexGroupDurationSec) {
}
