package com.solv.wefin.web.chat.groupChat.dto.response;

import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessageInfo;
import com.solv.wefin.domain.chat.groupChat.dto.info.ReplyMessageInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long messageId;
    private UUID userId;
    private Long groupId;
    private String messageType;
    private String sender;
    private String content;
    private OffsetDateTime createdAt;
    private ReplyMessageInfo replyTo;

    public static ChatMessageResponse from(ChatMessageInfo info) {
        return ChatMessageResponse.builder()
                .messageId(info.messageId())
                .userId(info.userId())
                .groupId(info.groupId())
                .messageType(info.messageType())
                .sender(info.sender())
                .content(info.content())
                .createdAt(info.createdAt())
                .replyTo(info.replyTo())
                .build();
    }
}
