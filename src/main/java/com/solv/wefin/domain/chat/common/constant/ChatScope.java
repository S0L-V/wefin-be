package com.solv.wefin.domain.chat.common.constant;

public final class ChatScope {

    public static final String GLOBAL = "GLOBAL";

    public static String group(Long roomId) {
        return "GROUP:" + roomId;
    }

    private ChatScope() {

    }
}
