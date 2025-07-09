package com.ob.ohlc_cache.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@UtilityClass
public class TypeUtil {
    private static final Map<Class<?>, Function<Object, ?>> CASTERS = new HashMap<>();

    static {
        CASTERS.put(String.class, v -> (v instanceof String) ? v : v.toString());
        CASTERS.put(Long.class, v -> (v instanceof Long) ? v : Long.valueOf(v.toString()));
        CASTERS.put(Integer.class, v -> (v instanceof Integer) ? v : Integer.valueOf(v.toString()));
        CASTERS.put(Double.class, v -> (v instanceof Double) ? v : Double.valueOf(v.toString()));
        CASTERS.put(Float.class, v -> (v instanceof Float) ? v : Float.valueOf(v.toString()));
        CASTERS.put(BigDecimal.class, v -> (v instanceof BigDecimal) ? v : new BigDecimal(v.toString()));
        CASTERS.put(Boolean.class, v -> (v instanceof Boolean) ? v : Boolean.valueOf(v.toString()));
    }

    @SuppressWarnings("unchecked")
    public static <T> T castType(Object value, Class<T> type) {
        if (value == null || type == null) return null;

        Function<Object, ?> caster = CASTERS.get(type);
        return (T) (caster != null ? caster.apply(value) : value);
    }

    public static void main(String[] args) {
        System.out.println(castType("0.0", BigDecimal.class));
    }
}
