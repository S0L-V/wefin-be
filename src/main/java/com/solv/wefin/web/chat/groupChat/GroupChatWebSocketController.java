package com.solv.wefin.web.chat.groupChat;

import com.solv.wefin.domain.chat.groupChat.service.ChatMessageService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.chat.groupChat.dto.request.ChatSendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GroupChatWebSocketController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/chat/group/send")
    public void sendMessage(ChatSendRequest request, SimpMessageHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        if(sessionAttributes == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Object userIdValue = sessionAttributes.get("userId");
        if(!(userIdValue instanceof UUID userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        chatMessageService.sendMessage(request.getContent(), userId);
    }
}
