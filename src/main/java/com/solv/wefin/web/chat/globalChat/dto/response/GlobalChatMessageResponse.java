package com.solv.wefin.web.chat.globalChat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private LocalDateTime createdAt;
}
