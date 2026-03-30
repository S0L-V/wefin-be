package com.solv.wefin.domain.chat.globalChat.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class GlobalChatMessageCreatedEvent {
    private final Long messageId;
    private final UUID userId;
    private final String role;
    private final String sender;
    private final String content;
    private final LocalDateTime createdAt;
}
