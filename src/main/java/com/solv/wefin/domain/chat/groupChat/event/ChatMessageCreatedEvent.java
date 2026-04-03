package com.solv.wefin.domain.chat.groupChat.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class ChatMessageCreatedEvent {
    private final Long messageId;
    private final UUID userId;
    private final Long groupId;
    private final String messageType;
    private final String sender;
    private final String content;
    private final OffsetDateTime createdAt;
}
