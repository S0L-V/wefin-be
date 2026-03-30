package com.solv.wefin.web.chat.globalChat.listener;

import com.solv.wefin.domain.chat.globalChat.event.GlobalChatMessageCreatedEvent;
import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GlobalChatMessageEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(GlobalChatMessageCreatedEvent event) {

        GlobalChatMessageResponse response = GlobalChatMessageResponse.builder()
                .messageId(event.getMessageId())
                .userId(event.getUserId())
                .role(event.getRole())
                .sender(event.getSender())
                .content(event.getContent())
                .createdAt(event.getCreatedAt())
                .build();

        messagingTemplate.convertAndSend("/topic/chat/global", response);
    }
}
