package com.solv.wefin.domain.chat.common.dto.info;

public record ChatUnreadInfo(
        long globalUnreadCount,
        long groupUnreadCount,
        Long lastReadGlobalMessageId,
        Long lastReadGroupMessageId
) {
    public boolean hasGlobalUnread() {
        return globalUnreadCount > 0;
    }

    public boolean hasGroupUnread() {
        return groupUnreadCount > 0;
    }

    public long totalUnreadCount() {
        return globalUnreadCount + groupUnreadCount;
    }
}
