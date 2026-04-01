package com.solv.wefin.domain.chat.common.constant;

import java.util.UUID;

public final class ChatScope {

    public static final String GLOBAL = "GLOBAL";

    private ChatScope() {
    }

    public static String globalKey(UUID userId) {
        return GLOBAL + ":" + userId;
    }
}

