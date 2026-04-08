package com.solv.wefin.global.util;

import java.math.BigDecimal;

public class ParseUtils {

    private ParseUtils() {}

    public static BigDecimal parseBigDecimal(String value) {
        return new BigDecimal(value == null || value.isBlank() ? "0" : value);
    }

    public static long parseLong(String value) {
        return value == null || value.isBlank() ? 0L : Long.parseLong(value);
    }

    public static float parseFloat(String value) {
        return value == null || value.isBlank() ? 0f : Float.parseFloat(value);
    }
}
