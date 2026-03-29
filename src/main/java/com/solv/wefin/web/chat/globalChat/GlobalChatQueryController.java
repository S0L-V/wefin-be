package com.solv.wefin.web.chat.globalChat;

import com.solv.wefin.domain.chat.globalChat.service.GlobalChatService;
import com.solv.wefin.web.chat.globalChat.dto.response.GlobalChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/global")
public class GlobalChatQueryController {

    private final GlobalChatService globalChatService;

    @GetMapping("/messages")
    public List<GlobalChatMessageResponse> getRecentMessage(@RequestParam(defaultValue = "50") int limit) {
        return globalChatService.getRecentMessages(limit);
    }
}
