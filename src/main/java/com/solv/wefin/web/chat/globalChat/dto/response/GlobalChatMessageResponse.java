package com.solv.wefin.web.chat.globalChat.dto.response;

import com.solv.wefin.domain.chat.globalChat.dto.GlobalChatMessageInfo;
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
public class GlobalChatMessageResponse {
    private Long messageId;
    private UUID userId;
    private String role;
    private String sender;
    private String content;
    private OffsetDateTime createdAt;

    public static GlobalChatMessageResponse from(GlobalChatMessageInfo info) {
        return GlobalChatMessageResponse.builder()
                .messageId(info.messageId())
                .userId(info.userId())
                .role(info.role())
                .sender(info.sender())
                .content(info.content())
                .createdAt(info.createdAt())
                .build();
    }
}
