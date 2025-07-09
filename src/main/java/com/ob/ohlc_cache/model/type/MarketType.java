package com.ob.ohlc_cache.model.type;

import lombok.Getter;

@Getter
public enum MarketType {
    FOREX(1), STOCK(2), CRYPTO(3);
    final int valueNumber;

    MarketType(int valueNumber) {
        this.valueNumber = valueNumber;
    }
    public static MarketType fromValue(int value) {
        for (MarketType MARKETTYPE : MarketType.values()) {
            if (MARKETTYPE.getValueNumber() == value) {
                return MARKETTYPE;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
    public static MarketType fromValue(String value) {
        for (MarketType MARKETTYPE : MarketType.values()) {
            if (MARKETTYPE.name().equalsIgnoreCase(value)) {
                return MARKETTYPE;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
