package com.ob.ohlc_cache.util;

import com.ob.ohlc_cache.model.cache.IndexKey;
import lombok.experimental.UtilityClass;
import net.openhft.chronicle.values.Values;

@UtilityClass
public class IndexKeyUtil {
    public static IndexKey copy(IndexKey from) {
        IndexKey target = Values.newHeapInstance(IndexKey.class);
        target.setSeriesId(from.getSeriesId());
        target.setIndexGroupTimestamp(from.getIndexGroupTimestamp());
        return target;
    }
}
