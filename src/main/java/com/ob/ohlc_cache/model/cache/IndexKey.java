package com.ob.ohlc_cache.model.cache;

import net.openhft.chronicle.bytes.Byteable;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.values.Group;
import net.openhft.chronicle.values.MaxUtf8Length;

public interface IndexKey extends Byteable, BytesMarshallable {
    @Group(1)
    String getSeriesId();
    void setSeriesId(@MaxUtf8Length(30) String seriesId);

    @Group(2)
    long getIndexGroupTimestamp();
    void setIndexGroupTimestamp(long timestamp);


}
