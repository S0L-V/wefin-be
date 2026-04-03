package com.solv.wefin.web.chat.groupChat.listener;

import com.solv.wefin.domain.chat.groupChat.event.ChatMessageCreatedEvent;
import com.solv.wefin.web.chat.groupChat.dto.response.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageCreatedEvent event) {

        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(event.getMessageId())
                .userId(event.getUserId())
                .groupId(event.getGroupId())
                .messageType(event.getMessageType())
                .sender(event.getSender())
                .content(event.getContent())
                .createdAt(event.getCreatedAt())
                .build();

        messagingTemplate.convertAndSend(
                String.format("/topic/chat/group/%d", event.getGroupId()),
                response);
    }
}
