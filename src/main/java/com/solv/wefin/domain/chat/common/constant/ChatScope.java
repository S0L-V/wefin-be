package com.solv.wefin.domain.chat.common.constant;

import java.util.UUID;

public final class ChatScope {

    public static final String GLOBAL = "GLOBAL";

    public static final String GROUP = "GROUP";

    private ChatScope() {
    }

    public static String globalKey(UUID userId) {
        return GLOBAL + ":" + userId;
    }

    public static String groupKey(Long groupId, UUID userId) {
        return GROUP + ":" + groupId + ":" + userId;
    }
}

