package com.solv.wefin.global.util;

public class StringUtils {

    private StringUtils() {
    }

    public static boolean containsKorean(String text) {
        return text != null && text.chars()
                .anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL);
    }
}
