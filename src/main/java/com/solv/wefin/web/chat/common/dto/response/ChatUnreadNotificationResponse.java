package com.solv.wefin.web.chat.common.dto.response;

import com.solv.wefin.domain.chat.common.dto.info.ChatUnreadInfo;
import lombok.Builder;

@Builder
public record ChatUnreadNotificationResponse(
        String chatType,
        Long messageId,
        Long groupId,
        String sender,
        String content,
        long globalUnreadCount,
        long groupUnreadCount,
        long totalUnreadCount,
        boolean hasGlobalUnread,
        boolean hasGroupUnread,
        Long lastReadGlobalMessageId,
        Long lastReadGroupMessageId
) {
    public static ChatUnreadNotificationResponse of(
            String chatType,
            Long messageId,
            Long groupId,
            String sender,
            String content,
            ChatUnreadInfo info
    ) {
        return ChatUnreadNotificationResponse.builder()
                .chatType(chatType)
                .messageId(messageId)
                .groupId(groupId)
                .sender(sender)
                .content(content)
                .globalUnreadCount(info.globalUnreadCount())
                .groupUnreadCount(info.groupUnreadCount())
                .totalUnreadCount(info.totalUnreadCount())
                .hasGlobalUnread(info.hasGlobalUnread())
                .hasGroupUnread(info.hasGroupUnread())
                .lastReadGlobalMessageId(info.lastReadGlobalMessageId())
                .lastReadGroupMessageId(info.lastReadGroupMessageId())
                .build();
    }
}
