package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalChatSendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GlobalChatService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendMessage(GlobalChatSendRequest request, String sender) {
        System.out.println("controller 진입");
        validateMessage(request.getContent());

        GlobalChatMessageResponse response = GlobalChatMessageResponse.builder()
                .messageId(null)
                .sender(sender)
                .content(request.getContent())
                .build();

        messagingTemplate.convertAndSend("/topic/chat/global", response);
    }

    private void validateMessage(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메시지 내용은 비어있을 수 없습니다.");
        }

        if (content.length() > 1000) {
            throw new IllegalArgumentException("메시지는 1000자를 초과할 수 없습니다.");
        }
    }
}
