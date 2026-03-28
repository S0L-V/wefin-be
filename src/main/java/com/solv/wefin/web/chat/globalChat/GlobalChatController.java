package com.solv.wefin.web.chat.globalChat;

import com.solv.wefin.domain.chat.globalChat.service.GlobalChatService;
import com.solv.wefin.web.chat.globalChat.dto.request.GlobalChatSendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class GlobalChatController {

    private final GlobalChatService globalChatService;

    @MessageMapping("/chat/global/send")
    public void sendMessage(GlobalChatSendRequest request, Principal principal) {
        String sender = (principal != null) ? principal.getName() : "anonymous";
        globalChatService.sendMessage(request, sender);
    }
}
