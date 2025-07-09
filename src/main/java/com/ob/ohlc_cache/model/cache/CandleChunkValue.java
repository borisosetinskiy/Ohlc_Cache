package com.ob.ohlc_cache.model.cache;

import com.ob.ohlc_cache.model.Candle;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

@Slf4j
public class CandleChunkValue implements BytesMarshallable, AutoCloseable {
    private static final int HEADER_SIZE = 24;
    private static final int CANDLE_SIZE = Candle.CANDLE_STRUCT_SIZE; // 52

    private static final int CAPACITY_OFFSET = 0;
    private static final int COUNT_OFFSET = 4;
    private static final int FIRST_TS_OFFSET = 8;
    private static final int LAST_TS_OFFSET = 16;

    private transient Bytes<?> page;

    public void init(int capacityInCandles) {
        if (page == null || page.capacity() == 0) {
            long pageSizeInBytes = getPageSize(capacityInCandles);
            page = Bytes.allocateDirect(pageSizeInBytes);
            page.writeInt(CAPACITY_OFFSET, capacityInCandles);
            page.writeInt(COUNT_OFFSET, 0);
            page.writeLong(FIRST_TS_OFFSET, Long.MAX_VALUE);
            page.writeLong(LAST_TS_OFFSET, 0L);
        }
    }


    /**
     * Статический метод для расчета необходимого размера страницы в байтах.
     */
    public static long getPageSize(int capacityInCandles) {
        return (long) HEADER_SIZE + (long) capacityInCandles * CANDLE_SIZE;
    }

    public int getCount() {
        return (page != null) ? page.readInt(COUNT_OFFSET) : 0;
    }

    /**
     * Возвращает максимальную емкость чанка в свечах.
     */
    public int getCapacityInCandles() {
        return (page != null) ? page.readInt(CAPACITY_OFFSET) : 0;
    }

    public long getFirstCandleTimestamp() {
        return (page != null) ? page.readLong(FIRST_TS_OFFSET) : 0;
    }

    public long getLastCandleTimestamp() {
        return (page != null) ? page.readLong(LAST_TS_OFFSET) : 0;
    }

    private void setFirstCandleTimestamp(long ts) {
        if (page != null) page.writeLong(FIRST_TS_OFFSET, ts);
    }

    private void setLastCandleTimestamp(long ts) {
        if (page != null) page.writeLong(LAST_TS_OFFSET, ts);
    }

    public boolean insert(long time, double o, double h, double l, double c, double vol, int tick) {
        if (page == null) {
            log.error("Page is not initialized, cannot insert candle.");
            return false;
        }

        int index = binarySearch(time);
        if (index >= 0) {
            return true;
        }

        int count = getCount();
        if (count >= getCapacityInCandles()) {
            return false;
        }

        index = -index - 1;

        long insertOffset = HEADER_SIZE + (long) index * CANDLE_SIZE;
        if (index < count) {
            page.move(insertOffset, insertOffset + CANDLE_SIZE, (long) (count - index) * CANDLE_SIZE);
        }

        page.writeDouble(insertOffset, h);
        page.writeDouble(insertOffset + 8, l);
        page.writeDouble(insertOffset + 16, o);
        page.writeDouble(insertOffset + 24, c);
        page.writeDouble(insertOffset + 32, vol);
        page.writeLong(insertOffset + 40, time);
        page.writeInt(insertOffset + 48, tick);
        page.writeInt(COUNT_OFFSET, count + 1);
        if (time < getFirstCandleTimestamp()) setFirstCandleTimestamp(time);
        if (time > getLastCandleTimestamp()) setLastCandleTimestamp(time);

        return true;
    }

    public void readCandle(int index, Candle flyweight) {
        if (page == null || index < 0 || index >= getCount()) {
            throw new IndexOutOfBoundsException("Index " + index + " is out of bounds for count " + getCount());
        }
        long offset = HEADER_SIZE + (long) index * CANDLE_SIZE;
        flyweight.bytesStore(page, offset, CANDLE_SIZE);
    }

    public int binarySearch(long timestamp) {
        if (page == null) return -1;
        int low = 0, high = getCount() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midTs = page.readLong(HEADER_SIZE + (long) mid * CANDLE_SIZE + 40);
            if (midTs < timestamp) low = mid + 1;
            else if (midTs > timestamp) high = mid - 1;
            else return mid;
        }
        return -(low + 1);
    }

    @Override
    public void writeMarshallable(BytesOut<?> out) {
        if (page == null) {
            out.writeLong(0);
            return;
        }

        long capacityInBytes = page.capacity();

        out.writeLong(capacityInBytes);
        out.write(page, 0, capacityInBytes );
    }

    public void clear() {
        if (page != null) {
            page.zeroOut(HEADER_SIZE, page.capacity());
            page.clear();
            page.writeInt(COUNT_OFFSET, 0);
        }
    }

    @Override
    public void readMarshallable(BytesIn<?> in) {
        long capacityInBytes = in.readLong();
        int lengthToRead = (int) capacityInBytes;
        if (capacityInBytes <= 0 || capacityInBytes > Integer.MAX_VALUE) {
            clear();
            return;
        }
        if (page == null || page.capacity() < capacityInBytes) {
            close();
            page = Bytes.allocateDirect(capacityInBytes);
        }
        in.read(page, lengthToRead);
        page.readPosition(0);
        page.readLimit(lengthToRead);
        page.writePosition(0);
        page.writeLimit(lengthToRead);
    }

    @Override
    public String toString() {
        if (page == null) return "{uninitialized}";
        return String.format("{count=%d, capacity=%d, timeRange=[%d...%d]}",
                getCount(),
                getCapacityInCandles(),
                getFirstCandleTimestamp(),
                getLastCandleTimestamp());
    }

    public void close() {
        if (page != null) {
            try {
                page.releaseLast();
            } catch (Exception ignored) {
            }
            page = null;
        }
    }
}


