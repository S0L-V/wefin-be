package com.solv.wefin.domain.chat.globalChat.event;

import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GlobalChatMessageEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(GlobalChatMessageCreatedEvent event) {
        GlobalChatMessageResponse message = event.getMessage();
        messagingTemplate.convertAndSend("/topic/chat/global", message);
    }
}
