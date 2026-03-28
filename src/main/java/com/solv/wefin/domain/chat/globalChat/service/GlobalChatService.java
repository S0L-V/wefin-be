package com.solv.wefin.domain.chat.globalChat.service;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
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

        String content = (request != null) ? request.getContent() : null;

        validateMessage(content);

        GlobalChatMessageResponse response = GlobalChatMessageResponse.builder()
                .messageId(null)
                .sender(sender)
                .content(content)
                .build();

        messagingTemplate.convertAndSend("/topic/chat/global", response);
    }

    private void validateMessage(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        if (content.length() > 1000) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }
    }
}
