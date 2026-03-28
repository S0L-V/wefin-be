package com.solv.wefin.web.chat.globalChat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalChatMessageResponse {
    private Long messageId;
    private String sender;
    private String content;
}
