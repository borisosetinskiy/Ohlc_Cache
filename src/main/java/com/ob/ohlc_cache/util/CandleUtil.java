package com.ob.ohlc_cache.util;

import com.ob.ohlc_cache.model.Candle;
import com.ob.ohlc_cache.model.Chart;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.Maths;
import net.openhft.chronicle.values.Values;

import static com.ob.ohlc_cache.model.Candle.CANDLE_STRUCT_SIZE;


public class CandleUtil {
    public static Candle newNativeCandle() {
        Candle candle = Values.newNativeReference(Candle.class);
        candle.bytesStore(Bytes.allocateDirect(CANDLE_STRUCT_SIZE), 0, CANDLE_STRUCT_SIZE);
        clear(candle);
        return candle;
    }

    public static Candle[] newNativeCandleArray(int size) {
        Candle[] candles = new Candle[size];
        for (int i = 0; i < size; i++) {
            Candle candle = newNativeCandle();
            candles[i] = candle;
        }
        return candles;
    }

    public static boolean validate(Candle candle) {
        return candle.getTime() > 0
               && candle.getClose() > 0
               && candle.getOpen() > 0
               && candle.getLow() > 0
               && candle.getHigh() > 0;
    }

    public static Candle newHeapCandle() {
        return newHeapCandle(0, 0, 0, 0, 0, 0, 0);
    }

    public static Candle newHeapCandle(double open, double close, double high, double low, double volume, long time, int tick) {
        Candle candle = Values.newHeapInstance(Candle.class);
        candle.setOpen(open);
        candle.setClose(close);
        candle.setHigh(high);
        candle.setLow(low);
        candle.setVolume(volume);
        candle.setTime(time);
        candle.setTick(tick);
        return candle;
    }

    public static void shiftRight(Candle[] candles, int k, int n) {
        if (candles == null || candles.length == 0 || n <= 0 || k < 0 || k >= candles.length) {
            return; // invalid input
        }

        int len = candles.length;

        // Сдвигаем элементы справа от конца массива к позиции k
        for (int i = len - 1; i >= k + n; i--) {
            copy(candles[i - n], candles[i]);
        }

        // Обнуляем (или зануляем) вставленные n элементов после позиции k
        for (int i = k; i < k + n && i < len; i++) {
            clearIfNeeded(candles[i]);
        }
    }


    public static void shiftLeft(Candle[] candles, int k, int n) {
        if (candles == null || candles.length == 0 || n <= 0 || k < 0 || k >= candles.length) {
            return; // invalid input
        }

        int len = candles.length;
        int moveLimit = len - n;

        // Сдвигаем элементы влево
        for (int i = k; i < moveLimit; i++) {
            copy(candles[i + n], candles[i]);
        }

        // Обнуляем последние n элементов
        for (int i = len - n; i < len; i++) {
            clearIfNeeded(candles[i]);
        }
    }

    public static void copy(Candle from, Candle to) {
        to.setOpen(from.getOpen());
        to.setClose(from.getClose());
        to.setHigh(from.getHigh());
        to.setLow(from.getLow());
        to.setVolume(from.getVolume());
        to.setTime(from.getTime());
        to.setTick(from.getTick());
    }

    public static boolean prependIfNewer(Candle[] candles, Candle newCandle, int filledSize) {
        if (newCandle == null || candles.length == 0 || filledSize == 0) return false;

        Candle first = candles[0];
        if (first == null || newCandle.getTime() > first.getTime()) {
            // shift only filledSize elements
            int copyLength = Math.min(filledSize, candles.length - 1);
            System.arraycopy(candles, 0, candles, 1, copyLength);
            candles[0] = newCandle;
            return true;
        }
        return false;
    }


    public static void appendValidLastCandle(Chart chart, Candle lastCandle) {
        Candle[] candles = chart.getCandles();
        int size = chart.getFilledSize();

        if (size >= candles.length) {
            size = candles.length - 1;
        }
        if (CandleUtil.prependIfNewer(candles, lastCandle, size)) {
            chart.setFilledSize(size + 1);
        }
    }

    public static int countNonZeroTimeCandle(Candle[] candles, int size) {
        int i = size - 1;
        while (i >= 0 && candles[i].getTime() == 0) {
            i--;
        }
        return i + 1;
    }


    public static Candle[] newHeapCandleArray(int size) {
        Candle[] candles = new Candle[size];
        for (int i = 0; i < size; i++) {
            Candle candle = newHeapCandle();
            candles[i] = candle;
        }
        return candles;
    }

    public static void release(Candle[] candles) {
        for (int i = 0; i < candles.length; i++) {
            release(candles[i]);
        }
    }

    public static void clear(Candle[] candles) {
        clearUsedCandles(candles);
    }

    public static  void clearUsedCandles(Candle[] candles) {
        int size = candles.length - 1;
        if (size <= 0) return;

        int i = 0;
        int bulkEnd = size & ~3; // max multiple of 4 under `size`

        // Unroll loop in blocks of 4
        for (; i < bulkEnd; i += 4) {
            clearIfNeeded(candles[i]);
            clearIfNeeded(candles[i + 1]);
            clearIfNeeded(candles[i + 2]);
            clearIfNeeded(candles[i + 3]);
        }

        // Clean remaining 1–3 elements
        for (; i < size; i++) {
            clearIfNeeded(candles[i]);
        }
    }

    public static void clearUnusedCandles(Candle[] candles, int size) {
            int len = candles.length;

            int remaining = len - size;
            if (remaining <= 0) return;

            int i = size;
            int bulkEnd = size + (remaining & ~3); // align to next lower multiple of 4

            // Unrolled loop in blocks of 4
            for (; i < bulkEnd; i += 4) {
                clearIfNeeded(candles[i]);
                clearIfNeeded(candles[i + 1]);
                clearIfNeeded(candles[i + 2]);
                clearIfNeeded(candles[i + 3]);
            }

            // Clean remaining 1–3 elements
            for (; i < len; i++) {
                clearIfNeeded(candles[i]);
            }
        }

    public static void clearIfNeeded(Candle c) {
        if (c != null && c.getTime() != 0) {
            c.setClose(0);
            c.setHigh(0);
            c.setLow(0);
            c.setOpen(0);
            c.setTime(0);
            c.setVolume(0);
            c.setTick(0);
        }
    }

    public static void clear(Candle candle) {
        if (candle != null) {
            candle.setClose(0);
            candle.setHigh(0);
            candle.setLow(0);
            candle.setOpen(0);
            candle.setTime(0);
            candle.setVolume(0);
            candle.setTick(0);
        }
    }

    public static void release(Candle candle) {
        if (candle != null) {
            try {
                candle.bytesStore().releaseLast();
            } catch (Exception e) {
            }
        }
    }

    public static boolean isEmpty(Candle candle) {
        return candle == null
               || candle.getTime() == 0
               || candle.getOpen() == 0
               || candle.getClose() == 0
               || candle.getHigh() == 0
               || candle.getLow() == 0;
    }

    public static void release(Bytes bytes) {
        if (bytes != null) {
            try {
                bytes.releaseLast();
            } catch (Exception e) {
            }
        }
    }

    public static boolean equals(Candle first, Candle second) {
        return first.getTime() == second.getTime()
               && first.getTick() == second.getTick()
               && Maths.same(first.getOpen(), second.getOpen())
               && Maths.same(first.getClose(), second.getClose())
               && Maths.same(first.getHigh(), second.getHigh())
               && Maths.same(first.getLow(), second.getLow())
               && Maths.same(first.getVolume(), second.getVolume());
    }

    public static void setOpenToClose(Candle previousCandle, Candle currentCandle, long timePeriod) {
        if (currentCandle.getTime() - previousCandle.getTime() > timePeriod) {
            return;
        }
        currentCandle.setOpen(previousCandle.getClose());
        if (currentCandle.getOpen() > currentCandle.getHigh()) {
            currentCandle.setHigh(currentCandle.getOpen());
        } else if (currentCandle.getOpen() < currentCandle.getLow()) {
            currentCandle.setLow(currentCandle.getOpen());
        }
    }

    public static String toString(Candle candle) {
        return candle.getOpen() +
               " " +
               candle.getHigh() +
               " " +
               candle.getLow() +
               " " +
               candle.getClose() +
               " " +
               candle.getVolume() +
               " " +
               TimeUtil.fromMillis(candle.getTime()) +
               " " +
               candle.getTick();
    }
}
