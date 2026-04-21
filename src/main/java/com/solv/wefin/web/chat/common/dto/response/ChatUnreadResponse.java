package com.solv.wefin.web.chat.common.dto.response;

import com.solv.wefin.domain.chat.common.dto.info.ChatUnreadInfo;

public record ChatUnreadResponse(
        long globalUnreadCount,
        long groupUnreadCount,
        long totalUnreadCount,
        boolean hasGlobalUnread,
        boolean hasGroupUnread,
        Long lastReadGlobalMessageId,
        Long lastReadGroupMessageId
) {
    public static ChatUnreadResponse from(ChatUnreadInfo info) {
        return new ChatUnreadResponse(
                info.globalUnreadCount(),
                info.groupUnreadCount(),
                info.totalUnreadCount(),
                info.hasGlobalUnread(),
                info.hasGroupUnread(),
                info.lastReadGlobalMessageId(),
                info.lastReadGroupMessageId()
        );
    }
}
