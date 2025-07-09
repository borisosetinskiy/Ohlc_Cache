package com.ob.ohlc_cache.model.cache;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

@Slf4j
public class IndexValue implements BytesMarshallable, AutoCloseable {
    private static final int HEADER_SIZE = 8;
    private static final int SIZE = 8;

    private static final int CAPACITY_OFFSET = 0;
    private static final int COUNT_OFFSET = 4;


    private transient Bytes<?> page;

    public void init(int capacityIndex) {
        if (page == null || page.capacity() == 0) {
            long pageSizeInBytes = getPageSize(capacityIndex);
            page = Bytes.allocateDirect(pageSizeInBytes);
            page.writeInt(CAPACITY_OFFSET, capacityIndex);
            page.writeInt(COUNT_OFFSET, 0);
        }
    }

    public static long getPageSize(int capacityIndex) {
        return (long) HEADER_SIZE + (long) capacityIndex * SIZE;
    }

    public int getCount() {
        return (page != null) ? page.readInt(COUNT_OFFSET) : 0;
    }

    public int getCapacity() {
        return (page != null) ? page.readInt(CAPACITY_OFFSET) : 0;
    }


    public boolean insert(long time) {
        if (page == null) {
            log.error("Page is not initialized, cannot insert candle.");
            return false;
        }

        int index = binarySearch(time);
        if (index >= 0) {
            return true;
        }

        int count = getCount();
        if (count >= getCapacity()) {
            return false;
        }

        index = -index - 1;

        long insertOffset = HEADER_SIZE + (long) index * SIZE;
        if (index < count) {
            page.move(insertOffset, insertOffset + SIZE, (long) (count - index) * SIZE);
        }
        page.writeLong(insertOffset, time);
        page.writeInt(COUNT_OFFSET, count + 1);
        log.info("Inserted in IndexValue timestamp {} at index {}", time, index);
        return true;
    }

    public long getTimestampByIndex(int index) {
        if (page == null || index < 0 || index >= getCount()) {
            throw new IndexOutOfBoundsException();
        }
        return page.readLong(HEADER_SIZE + (long) index * SIZE);
    }

    public int binarySearch(long timestamp) {
        if (page == null) return -1;
        int low = 0, high = getCount() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midTs = page.readLong(HEADER_SIZE + (long) mid * SIZE);
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
        out.write(page, 0, capacityInBytes);
        log.info("IndexValue written to out {}", getCount());
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
        log.info("IndexValue read from in {}", getCount());
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


