package com.ob.ohlc_cache.model;

import net.openhft.chronicle.bytes.Byteable;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.values.Group;
import net.openhft.chronicle.values.Values;

public interface Candle extends Byteable, BytesMarshallable {
    int CANDLE_STRUCT_SIZE = 52;

    @Group(1) double getHigh();
    void setHigh(double high);
    @Group(2) double getLow();
    void setLow(double low);
    @Group(3) double getOpen();
    void setOpen(double open);
    @Group(4) double getClose();
    void setClose(double close);
    @Group(5) double getVolume();
    void setVolume(double volume);
    @Group(6) long getTime();
    void setTime(long time);
    @Group(7) int getTick();
    void setTick(int tick);

    default void copyReusable(Candle to) {
        if (to == null) return;
        to.setHigh(getHigh());
        to.setLow(getLow());
        to.setOpen(getOpen());
        to.setClose(getClose());
        to.setVolume(getVolume());
        to.setTime(getTime());
        to.setTick(getTick());
    }
    default Candle copy() {
        Candle copy = Values.newHeapInstance(Candle.class);
        copy.setHigh(getHigh());
        copy.setLow(getLow());
        copy.setOpen(getOpen());
        copy.setClose(getClose());
        copy.setVolume(getVolume());
        copy.setTime(getTime());
        copy.setTick(getTick());
        return copy;
    }
}
