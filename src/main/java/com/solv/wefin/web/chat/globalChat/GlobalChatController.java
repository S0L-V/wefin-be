package com.solv.wefin.web.chat.globalChat;

import com.solv.wefin.domain.chat.globalChat.service.GlobalChatService;
import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalChatSendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GlobalChatController {

    private final GlobalChatService globalChatService;

    @MessageMapping("/chat/global/send")
    public void sendMessage(GlobalChatSendRequest request, SimpMessageHeaderAccessor accessor) {

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if(sessionAttributes == null || sessionAttributes.get("userId") == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        UUID userId = (UUID) sessionAttributes.get("userId");
        globalChatService.sendMessage(request, userId);
    }
}
