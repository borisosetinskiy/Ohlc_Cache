package com.ob.ohlc_cache.model.cache;

import net.openhft.chronicle.bytes.Byteable;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.values.Group;
import net.openhft.chronicle.values.MaxUtf8Length;
import net.openhft.chronicle.values.Values;

public interface ChunkKey extends Byteable, BytesMarshallable {

    @Group(1)
    String getSymbol();

    void setSymbol(@MaxUtf8Length(24) String symbol);
    @Group(2)
    long getDuration();

    void setDuration(long duration);
    @Group(3)
    long getChunkStartTimestamp();

    void setChunkStartTimestamp(long timestamp);


    static ChunkKey of(String symbol, long duration, long chunkStartTimestamp) {
        final ChunkKey key = Values.newHeapInstance(ChunkKey.class);
        key.setSymbol(symbol);
        key.setDuration(duration);
        key.setChunkStartTimestamp(chunkStartTimestamp);
        return key;
    }
}
