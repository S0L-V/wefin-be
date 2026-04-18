package com.solv.wefin.domain.chat.groupChat.event;

import com.solv.wefin.domain.chat.groupChat.dto.info.ChatMessageInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ChatMessageCreatedEvent {
    private final Long groupId;
    private final ChatMessageInfo message;
}
