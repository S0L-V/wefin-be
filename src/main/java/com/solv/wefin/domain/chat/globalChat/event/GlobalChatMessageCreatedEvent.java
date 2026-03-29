package com.solv.wefin.domain.chat.globalChat.event;

import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GlobalChatMessageCreatedEvent {
    private final GlobalChatMessageResponse message;
}
